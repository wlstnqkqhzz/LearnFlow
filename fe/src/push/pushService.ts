import axios from 'axios'
import { pushApi, type PushConfig } from '../api/pushApi.ts'
import { authSession } from '../auth/authSession.ts'
import { createPushBrowser, subscriptionRequest, type PushBrowser } from './pushBrowser.ts'

type Binding = { memberId: number; subscriptionId: number | null; fingerprint: string }
type Pending = { memberId: number; subscriptionId: number }
type Ledger = { active: Binding | null; pending: Pending[] }
export type PushStatus = 'loading' | 'unsupported' | 'default' | 'off' | 'on' | 'denied' | 'disabled' | 'error' | 'other-account'
export type PushState = { status: PushStatus; busy: boolean; error: string; canEnable: boolean; canDisable: boolean }
const initial: PushState = { status: 'loading', busy: false, error: '', canEnable: false, canDisable: false }
type Session = { memberId: number | null; generation: number }
function statusOf(error: unknown): number | undefined { return axios.isAxiosError(error) ? error.response?.status : undefined }
const positive = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value > 0
function ledgerFrom(raw: string | null): Ledger {
  if (!raw) return { active: null, pending: [] }
  const parsed: unknown = JSON.parse(raw)
  if (!parsed || typeof parsed !== 'object' || !('active' in parsed) || !('pending' in parsed) || !Array.isArray(parsed.pending)) throw new Error('Invalid binding')
  const active = parsed.active
  if (active !== null && (!active || typeof active !== 'object' || !('memberId' in active) || !positive(active.memberId)
    || !('subscriptionId' in active) || !(active.subscriptionId === null || positive(active.subscriptionId))
    || !('fingerprint' in active) || typeof active.fingerprint !== 'string')) throw new Error('Invalid binding')
  const pending: Pending[] = parsed.pending.map((entry: unknown) => {
    if (!entry || typeof entry !== 'object' || !('memberId' in entry) || !positive(entry.memberId)
      || !('subscriptionId' in entry) || !positive(entry.subscriptionId)) throw new Error('Invalid binding')
    return { memberId: entry.memberId, subscriptionId: entry.subscriptionId }
  })
  return { active: active as Binding | null, pending }
}

// Tokens/keys/payloads never enter this ledger or the Service Worker.
export function createPushService(browser: PushBrowser, api: typeof pushApi, session: () => Session) {
  let state: PushState = initial
  let owner: Session | null = null
  let config: PushConfig | null = null
  let epoch = 0
  let tail: Promise<unknown> = Promise.resolve()
  let notice = ''
  const listeners = new Set<() => void>()
  const current = (who: Session) => session().memberId === who.memberId && session().generation === who.generation
  function publish(patch: Partial<PushState>) { state = { ...state, ...patch }; listeners.forEach(listener => listener()) }
  function check(who: Session, operation: number) {
    if (!current(who) || operation !== epoch) throw new Error('Session changed')
  }
  function serial<T>(action: () => Promise<T>) {
    const result = tail.then(() => browser.lock(action))
    tail = result.catch(() => undefined)
    return result
  }
  const read = () => ledgerFrom(browser.read())
  const save = (ledger: Ledger) => browser.write(JSON.stringify(ledger))
  async function disableId(id: number) {
    try { await api.disable(id) } catch (error) { if (statusOf(error) !== 404) throw error }
  }
  async function pendingCleanup(ledger: Ledger, who: Session) {
    for (const entry of [...ledger.pending]) {
      if (entry.memberId !== who.memberId) continue
      if (!current(who)) throw new Error('Session changed')
      await disableId(entry.subscriptionId)
      ledger.pending = ledger.pending.filter(value => value !== entry)
      save(ledger)
    }
  }
  function retire(ledger: Ledger) {
    const binding = ledger.active
    if (binding?.subscriptionId && !ledger.pending.some(p => p.subscriptionId === binding.subscriptionId))
      ledger.pending.push({ memberId: binding.memberId, subscriptionId: binding.subscriptionId })
    ledger.active = null
    save(ledger)
  }
  async function removeCurrent(ledger: Ledger, who: Session, removeUnknown = false) {
    let failed = false
    let serverDisabled = false
    const binding = ledger.active
    if (binding && binding.memberId !== who.memberId && !removeUnknown) throw new Error('Another account owns this subscription')
    const subscription = await browser.current()
    if (binding?.memberId === who.memberId && current(who)) {
      try {
        // Recover the ID after an uncertain PUT response before disabling it.
        if (binding.subscriptionId === null && subscription && await browser.fingerprint(subscription) === binding.fingerprint) {
          binding.subscriptionId = (await api.register(subscriptionRequest(subscription))).subscriptionId
          save(ledger)
        }
        if (binding.subscriptionId !== null) {
          await disableId(binding.subscriptionId)
          serverDisabled = true
        }
      } catch { failed = true }
    }
    try {
      if (subscription && !await subscription.unsubscribe() && await browser.current()) failed = true
    } catch { failed = true }
    if (!await browser.current()) {
      if (serverDisabled) { ledger.active = null; save(ledger) }
      else retire(ledger)
    }
    if (failed) throw new Error('해제 확인 필요')
  }
  async function prepare() {
    const who = session()
    if (who.memberId === null) return
    if (state.busy && owner && current(owner)) return
    owner = who
    const operation = epoch
    const unsupported = browser.unsupportedReason()
    if (unsupported) { publish({ ...initial, status: 'unsupported', error: unsupported }); return }
    publish({ busy: true, error: '' })
    try {
      await serial(async () => {
        check(who, operation)
        const ledger = read(); save(ledger) // Storage must work before allowing any registration.
        await browser.prepare(); check(who, operation)
        await pendingCleanup(ledger, who)
        config = await api.config(); check(who, operation)
        const subscription = await browser.current(); check(who, operation)
        if (ledger.active && ledger.active.memberId !== who.memberId) {
          publish({ status: 'other-account', canEnable: false, canDisable: false }); return
        }
        const permission = browser.permission()
        let enabled = false
        if (subscription && ledger.active?.subscriptionId && await browser.fingerprint(subscription) === ledger.active.fingerprint) {
          try {
            const response = await api.get(ledger.active.subscriptionId)
            enabled = response.enabled && (response.expirationTime === null || Date.parse(response.expirationTime) > Date.now())
          } catch (error) { if (statusOf(error) !== 404) throw error }
        }
        check(who, operation)
        const status: PushStatus = permission === 'denied' ? 'denied' : !config.enabled ? 'disabled'
          : permission === 'default' ? 'default' : enabled ? 'on' : 'off'
        publish({ status, canEnable: !!config.enabled && !!config.publicKey && permission !== 'denied' && status !== 'on',
          canDisable: !!subscription || !!ledger.active || ledger.pending.some(p => p.memberId === who.memberId) })
      })
    } catch {
      if (current(who) && operation === epoch) publish({ status: 'error', error: '알림 설정을 확인하지 못했습니다. 저장소·네트워크 상태를 확인하고 다시 시도해 주세요.', canEnable: false, canDisable: true })
    } finally { if (current(who) && operation === epoch) publish({ busy: false }) }
  }
  // Called directly from the click handler, before awaits/locks can consume user activation.
  async function enable() {
    const who = session()
    if (who.memberId === null || state.busy || !state.canEnable || !config?.publicKey) return
    const operation = epoch
    const key = config.publicKey
    publish({ busy: true, error: '' })
    try {
      const permission = browser.permission() === 'default' ? await browser.requestPermission() : browser.permission()
      check(who, operation)
      if (permission !== 'granted') {
        publish({ status: permission === 'denied' ? 'denied' : 'default', canEnable: permission !== 'denied' }); return
      }
      await serial(async () => {
        check(who, operation)
        const ledger = read()
        if (ledger.active && ledger.active.memberId !== who.memberId) throw new Error('Account binding changed')
        let subscription = await browser.current(); check(who, operation)
        // Unowned browser state cannot safely be attached to a new login.
        if (subscription && (!ledger.active || await browser.fingerprint(subscription) !== ledger.active.fingerprint)) {
          if (!await subscription.unsubscribe() && await browser.current()) throw new Error('Unsubscribe failed')
          retire(ledger); subscription = null
        }
        if (!subscription && ledger.active) retire(ledger)
        await pendingCleanup(ledger, who)
        subscription ??= await browser.subscribe(key)
        if (!current(who) || operation !== epoch) { await subscription.unsubscribe(); return }
        ledger.active = { memberId: who.memberId!, subscriptionId: ledger.active?.subscriptionId ?? null, fingerprint: await browser.fingerprint(subscription) }
        save(ledger)
        check(who, operation)
        const response = await api.register(subscriptionRequest(subscription))
        ledger.active.subscriptionId = response.subscriptionId; save(ledger)
        check(who, operation)
        if (!response.enabled) throw new Error('Registration disabled')
        publish({ status: 'on', canEnable: false, canDisable: true })
      })
    } catch (error) {
      if (current(who) && operation === epoch) publish({ status: 'error', canDisable: true,
        error: statusOf(error) === 409 ? '구독 충돌 또는 등록 개수 제한입니다. 알림 끄기로 구독을 정리한 후 다시 켜 주세요.'
          : 'Push 등록에 실패했습니다. 권한과 연결 상태를 확인하고 다시 시도해 주세요.' })
    } finally { if (current(who) && operation === epoch) publish({ busy: false }) }
  }
  async function disable() {
    const who = session()
    if (who.memberId === null || state.busy) return
    ++epoch
    publish({ busy: true, error: '' })
    try {
      await serial(async () => { const ledger = read(); try { await pendingCleanup(ledger, who) } finally { await removeCurrent(ledger, who) } })
      if (current(who)) publish({ status: browser.permission() === 'denied' ? 'denied' : browser.permission() === 'default' ? 'default' : 'off', canDisable: false,
        canEnable: !!config?.enabled && browser.permission() !== 'denied' })
    } catch { if (current(who)) publish({ status: 'error', canEnable: false, canDisable: true, error: '알림 해제가 일부 완료되지 않았습니다. 알림 끄기를 다시 눌러 주세요.' }) }
    finally { if (current(who)) publish({ busy: false }) }
  }
  async function cleanupForLogout() {
    ++epoch // Cancel permission prompts that have not returned yet.
    notice = ''
    if (browser.unsupportedReason()) return
    const who = session()
    try {
      await serial(async () => {
        const ledger = read()
        // A tab signed into A must not unsubscribe the browser binding owned by B.
        if (ledger.active && ledger.active.memberId !== who.memberId) return
        try { await pendingCleanup(ledger, who) } finally { await removeCurrent(ledger, who, true) }
      })
    } catch { notice = '브라우저 Push 해제를 확인하지 못했습니다. 로그아웃은 완료되며, 브라우저 사이트 설정에서 알림을 차단할 수 있습니다.' }
    publish({ ...initial })
  }
  async function beforeAccount(nextMemberId: number) {
    ++epoch
    const generation = session().generation
    if (browser.unsupportedReason()) return
    try {
      await serial(async () => {
        if (session().generation !== generation) return
        const ledger = read()
        if (ledger.active?.memberId === nextMemberId) return
        const subscription = await browser.current()
        if (subscription && !await subscription.unsubscribe() && await browser.current()) throw new Error('Unsubscribe failed')
        retire(ledger)
      })
    } catch {
      // Leave the old owner intact. UI blocks enabling until cleanup succeeds.
      publish({ ...initial, status: 'error', error: '이전 계정의 Push 구독을 정리하지 못했습니다. 이전 계정에서 알림을 끈 뒤 다시 시도해 주세요.' })
    }
  }
  return {
    getNotice: () => notice,
    getSnapshot: () => owner && current(owner) ? state : initial,
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener) } },
    prepare, enable, disable, cleanupForLogout, beforeAccount,
  }
}
export const pushService = createPushService(createPushBrowser(), pushApi, () => ({
  memberId: authSession.getSnapshot().user?.memberId ?? null, generation: authSession.getGeneration(),
}))
