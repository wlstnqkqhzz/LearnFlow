import assert from 'node:assert/strict'
import { test } from 'node:test'
import { AxiosError } from 'axios'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'
import { createPushService, pushService } from '../src/push/pushService.ts'
import { createPushBrowser, subscriptionRequest, vapidBytes } from '../src/push/pushBrowser.ts'
import { pushApi } from '../src/api/pushApi.ts'
import { apiClient } from '../src/api/client.ts'
import { authApi } from '../src/api/authApi.ts'
import { authSession } from '../src/auth/authSession.ts'
import { login as loginAction, logout } from '../src/auth/authActions.ts'
import { createNotificationEntry, notificationReturnPath } from '../src/components/notification/notificationEntry.ts'
import { createNotificationStore } from '../src/components/notification/notificationStore.ts'
import { listenForNotificationChanges, signalNotificationChanges } from '../src/push/pushMessages.ts'

const tick = () => new Promise(resolve => setImmediate(resolve))
function deferred() { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
const response = { subscriptionId: 7, enabled: true, expirationTime: null, createdAt: '2026-09-30T00:00:00Z', updatedAt: '2026-09-30T00:00:00Z' }
function fixture({ permission = 'granted', owner = null, existing = false, unsupported = '' } = {}) {
  const calls = []
  const env = { permission, session: { memberId: 1, generation: 1 }, raw: owner ? JSON.stringify({ active: { memberId: owner, subscriptionId: 7, fingerprint: 'hash-existing' }, pending: [] }) : null,
    subscription: null, registerError: false, deleteError: false, unsubscribeError: false, subscribeError: false, config: { enabled: true, publicKey: 'public-key' } }
  function makeSub(name) {
    return { endpoint: `https://push.example/${name}`, expirationTime: null, toJSON: () => ({ keys: { p256dh: 'client-key', auth: 'client-auth' } }),
      async unsubscribe() { calls.push('unsubscribe'); if (env.unsubscribeError) throw Error('private error'); env.subscription = null; return true } }
  }
  if (existing) env.subscription = makeSub('existing')
  const browser = {
    unsupportedReason: () => unsupported, permission: () => env.permission,
    async requestPermission() { calls.push('permission'); env.permission = 'granted'; return env.permission },
    async prepare() { calls.push('prepare') }, async current() { return env.subscription },
    async subscribe(key) { calls.push(['subscribe', key]); if (env.subscribeError) throw Error('secret'); return env.subscription = makeSub('new') },
    async fingerprint(sub) { return sub.endpoint.endsWith('existing') ? 'hash-existing' : 'hash-new' },
    read: () => env.raw, write: value => { env.raw = value }, lock: action => action(),
  }
  const api = {
    async config() { calls.push('config'); return env.config },
    async get(id) { calls.push(['get', id]); return { ...response } },
    async register(request) { calls.push(['register', request]); if (env.registerError) throw Error('private JWT key'); return { ...response } },
    async disable(id) { calls.push(['disable', id]); if (env.deleteError) throw Error('private Authorization'); },
  }
  return { env, calls, browser, api, service: createPushService(browser, api, () => env.session) }
}
const names = calls => calls.map(call => Array.isArray(call) ? call[0] : call)

await test('미지원 환경은 SW/API/권한 요청을 하지 않는다', async () => {
  const f = fixture({ unsupported: '미지원' }); await f.service.prepare(); await f.service.enable()
  assert.equal(f.service.getSnapshot().status, 'unsupported'); assert.deepEqual(f.calls, [])
  assert.match(createPushBrowser().unsupportedReason(), /HTTPS|localhost/)
})
await test('브라우저 adapter는 secure context/SW/PushManager/Notification을 검사하고 루트 scope로 등록한다', async () => {
  const oldLocation = Object.getOwnPropertyDescriptor(globalThis, 'location')
  const oldLocks = Object.getOwnPropertyDescriptor(navigator, 'locks')
  const registration = { scope: 'http://localhost:5173/', pushManager: { async getSubscription() { return null } } }
  const calls = []
  Object.defineProperty(globalThis, 'window', { configurable: true, value: { isSecureContext: false } })
  Object.defineProperty(globalThis, 'location', { configurable: true, value: { origin: 'http://localhost:5173' } })
  try {
    const browser = createPushBrowser(); assert.match(browser.unsupportedReason(), /HTTPS/)
    window.isSecureContext = true; assert.match(browser.unsupportedReason(), /지원하지/)
    Object.defineProperty(navigator, 'serviceWorker', { configurable: true, value: {
      async register(...args) { calls.push(args); return registration }, ready: Promise.resolve(registration),
    } })
    window.PushManager = class {}; assert.match(browser.unsupportedReason(), /지원하지/)
    window.Notification = class {}
    Object.defineProperty(navigator, 'locks', { configurable: true, value: { request: (name, action) => action() } })
    assert.equal(browser.unsupportedReason(), ''); await browser.prepare(); assert.equal(await browser.current(), null)
    assert.deepEqual(calls, [['/sw.js', { scope: '/', updateViaCache: 'none' }]])
    registration.scope = 'http://localhost:5173/other/'; await assert.rejects(browser.prepare(), /범위/)
  } finally {
    delete globalThis.window; delete navigator.serviceWorker
    if (oldLocation) Object.defineProperty(globalThis, 'location', oldLocation); else delete globalThis.location
    if (oldLocks) Object.defineProperty(navigator, 'locks', oldLocks); else delete navigator.locks
  }
})
await test('default 권한은 명시적인 켜기에서만 요청하고 서버 등록 뒤에 on을 표시한다', async () => {
  const f = fixture({ permission: 'default' }); await f.service.prepare()
  assert.equal(f.service.getSnapshot().status, 'default'); assert.deepEqual(f.calls, ['prepare', 'config'])
  const gate = deferred(); const register = f.api.register
  f.api.register = async request => { await gate.promise; return register(request) }
  const pending = f.service.enable(); await tick()
  assert.equal(f.service.getSnapshot().busy, true); assert.notEqual(f.service.getSnapshot().status, 'on')
  gate.resolve(); await pending
  assert.equal(f.service.getSnapshot().status, 'on')
  assert.deepEqual(names(f.calls), ['prepare', 'config', 'permission', 'subscribe', 'register'])
  assert.deepEqual(f.calls.at(-1)[1], { endpoint: 'https://push.example/new', p256dh: 'client-key', auth: 'client-auth', expirationTime: null })
  assert.doesNotMatch(f.env.raw, /https:|client-key|client-auth|token/i)
})
await test('granted는 재요청하지 않고 denied는 켜기를 차단한다', async () => {
  for (const permission of ['granted', 'denied']) {
    const f = fixture({ permission }); await f.service.prepare(); await f.service.enable()
    assert.ok(!names(f.calls).includes('permission'))
    assert.equal(f.service.getSnapshot().status, permission === 'granted' ? 'on' : 'denied')
  }
})
await test('사용자가 권한 창을 거부하거나 닫으면 subscribe하지 않는다', async () => {
  for (const result of ['denied', 'default']) {
    const f = fixture({ permission: 'default' }); f.browser.requestPermission = async () => result
    await f.service.prepare(); await f.service.enable()
    assert.equal(f.service.getSnapshot().status, result); assert.ok(!names(f.calls).includes('subscribe'))
  }
})
await test('같은 계정의 기존 브라우저 구독을 재사용하고 상태 API로 확인한다', async () => {
  const f = fixture({ owner: 1, existing: true }); f.api.get = async () => ({ ...response, enabled: false })
  await f.service.prepare(); await f.service.enable()
  assert.ok(!names(f.calls).includes('subscribe')); assert.ok(!names(f.calls).includes('unsubscribe'))
  assert.equal(f.calls.at(-1)[1].endpoint, 'https://push.example/existing')
  assert.equal(f.service.getSnapshot().status, 'on')
  const enabled = fixture({ owner: 1, existing: true }); await enabled.service.prepare()
  assert.equal(enabled.service.getSnapshot().status, 'on'); assert.deepEqual(enabled.calls.at(-1), ['get', 7])
})
await test('만료된 서버 구독은 켜짐으로 표시하지 않는다', async () => {
  const f = fixture({ owner: 1, existing: true }); f.api.get = async () => ({ ...response, expirationTime: '2000-01-01T00:00:00Z' })
  await f.service.prepare(); assert.equal(f.service.getSnapshot().status, 'off')
})
await test('SW 준비/브라우저 subscribe/서버 등록 실패를 안전한 재시도 UI로 표시한다', async () => {
  const sw = fixture(); sw.browser.prepare = async () => { throw Error('sensitive') }
  await sw.service.prepare(); assert.equal(sw.service.getSnapshot().status, 'error')
  for (const key of ['subscribeError', 'registerError']) {
    const f = fixture(); f.env[key] = true; await f.service.prepare(); await f.service.enable()
    assert.equal(f.service.getSnapshot().status, 'error'); assert.equal(f.service.getSnapshot().busy, false)
    assert.doesNotMatch(f.service.getSnapshot().error, /private|JWT|secret/)
    f.env[key] = false; await f.service.enable(); assert.equal(f.service.getSnapshot().status, 'on')
    if (key === 'registerError') assert.equal(names(f.calls).filter(c => c === 'subscribe').length, 1)
  }
})
await test('끄기는 서버 비활성화 → 브라우저 해제 순서이며 다른 기기를 요청하지 않는다', async () => {
  const f = fixture({ owner: 1, existing: true }); await f.service.prepare(); f.calls.length = 0
  await f.service.disable()
  assert.deepEqual(f.calls, [['disable', 7], 'unsubscribe']); assert.equal(f.env.subscription, null)
  assert.equal(f.service.getSnapshot().status, 'off'); assert.equal(JSON.parse(f.env.raw).active, null)
})
await test('서버 해제 실패에도 브라우저를 해제하고 미완료 서버 ID만 재시도한다', async () => {
  const f = fixture({ owner: 1, existing: true }); await f.service.prepare(); f.env.deleteError = true
  await f.service.disable(); assert.equal(f.env.subscription, null); assert.equal(f.service.getSnapshot().status, 'error')
  assert.deepEqual(JSON.parse(f.env.raw).pending, [{ memberId: 1, subscriptionId: 7 }])
  f.env.deleteError = false; await f.service.disable()
  assert.equal(f.service.getSnapshot().status, 'off'); assert.deepEqual(JSON.parse(f.env.raw).pending, [])
})
await test('브라우저 해제 실패 재시도는 서버 구독을 재등록/재활성화하지 않는다', async () => {
  const f = fixture({ owner: 1, existing: true }); await f.service.prepare(); f.env.unsubscribeError = true
  await f.service.disable(); assert.equal(f.service.getSnapshot().status, 'error')
  f.env.unsubscribeError = false; await f.service.disable()
  assert.equal(f.env.subscription, null); assert.ok(!names(f.calls).includes('register'))
})
await test('불확실한 등록 응답 뒤 끄기는 멱등 PUT으로 ID를 복구한 후 비활성화한다', async () => {
  const f = fixture(); f.env.registerError = true; await f.service.prepare(); await f.service.enable()
  f.env.registerError = false; f.calls.length = 0; await f.service.disable()
  assert.deepEqual(names(f.calls), ['register', 'disable', 'unsubscribe'])
})
await test('서버 Push가 꺼져 있어도 기존 브라우저 구독은 해제할 수 있다', async () => {
  const f = fixture({ owner: 1, existing: true }); f.env.config = { enabled: false, publicKey: null }
  await f.service.prepare(); assert.equal(f.service.getSnapshot().canEnable, false)
  assert.equal(f.service.getSnapshot().canDisable, true); await f.service.disable(); assert.equal(f.env.subscription, null)
})
await test('로그아웃 정리는 실패를 전파하지 않으며 사용자 안내를 남긴다', async () => {
  const f = fixture({ owner: 1, existing: true }); f.env.deleteError = true; f.env.unsubscribeError = true
  await assert.doesNotReject(f.service.cleanupForLogout()); assert.match(f.service.getNotice(), /로그아웃은 완료/)
  assert.ok(names(f.calls).includes('unsubscribe'))
})
await test('권한 팝업 대기 중 로그아웃은 차단되지 않고 늦게 허용해도 등록하지 않는다', async () => {
  const f = fixture({ permission: 'default' }); const gate = deferred(); f.browser.requestPermission = () => gate.promise
  await f.service.prepare(); const pending = f.service.enable(); await f.service.cleanupForLogout()
  f.env.session = { memberId: null, generation: 2 }; gate.resolve('granted'); await pending
  assert.ok(!names(f.calls).includes('register')); assert.ok(!names(f.calls).includes('subscribe'))
})
await test('진행 중인 서버 등록 완료 뒤 로그아웃 정리가 등록 ID를 비활성화한다', async () => {
  const f = fixture(); const gate = deferred(); f.api.register = async () => { await gate.promise; return response }
  await f.service.prepare(); const pending = f.service.enable(); await tick()
  const cleanup = f.service.cleanupForLogout(); gate.resolve(); await Promise.all([pending, cleanup])
  assert.ok(f.calls.some(c => Array.isArray(c) && c[0] === 'disable' && c[1] === 7)); assert.equal(f.env.subscription, null)
})
await test('계정 전환은 이전 구독을 해제하고 새 endpoint를 등록한다', async () => {
  const f = fixture({ owner: 1, existing: true }); await f.service.beforeAccount(2)
  assert.equal(f.env.subscription, null); assert.equal(JSON.parse(f.env.raw).active, null)
  f.env.session = { memberId: 2, generation: 2 }; await f.service.prepare(); await f.service.enable()
  assert.equal(JSON.parse(f.env.raw).active.memberId, 2)
  assert.equal(f.calls.find(c => Array.isArray(c) && c[0] === 'register')[1].endpoint, 'https://push.example/new')
  assert.ok(!names(f.calls).includes('disable')) // Old account's server ID never sent under the new account.
})
await test('소유자 정보 없는 브라우저 구독은 계정 안전을 위해 교체한다', async () => {
  const f = fixture({ existing: true }); await f.service.prepare(); await f.service.enable()
  assert.ok(names(f.calls).indexOf('unsubscribe') < names(f.calls).indexOf('subscribe'))
})
await test('다른 탭 계정의 구독은 켜기/끄기/로그아웃으로 변경하지 않는다', async () => {
  const f = fixture({ owner: 2, existing: true }); await f.service.prepare()
  assert.equal(f.service.getSnapshot().status, 'other-account'); await f.service.enable(); await f.service.disable(); await f.service.cleanupForLogout()
  assert.ok(f.env.subscription); assert.ok(!names(f.calls).includes('unsubscribe')); assert.ok(!names(f.calls).includes('disable'))
})
await test('Subscription 직렬화는 실제 DTO의 평평한 키와 nullable ISO 만료 시각을 사용한다', () => {
  const f = fixture({ existing: true }); f.env.subscription.expirationTime = 1000
  assert.equal(subscriptionRequest(f.env.subscription).expirationTime, '1970-01-01T00:00:01.000Z')
  assert.throws(() => subscriptionRequest({ ...f.env.subscription, toJSON: () => ({}) }))
  const bytes = Buffer.alloc(65); bytes[0] = 4
  assert.deepEqual([...vapidBytes(bytes.toString('base64url'))], [...bytes]); assert.throws(() => vapidBytes('invalid'))
})

const storage = new Map()
Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, value: {
  getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key),
} })
function tokens(id = 1, roles = ['EMPLOYEE']) {
  return { accessToken: `test.${Buffer.from(JSON.stringify({ memberId: id, email: 'test@example.com', roles, tokenType: 'ACCESS', exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url')}.test`,
    refreshToken: 'test-refresh', tokenType: 'Bearer', accessTokenExpiresInSeconds: 3600, refreshTokenExpiresInSeconds: 7200 }
}
function signIn(id = 1, roles) { authSession.clear(); authSession.accept(tokens(id, roles), authSession.getGeneration()) }
function adapter(handler) { apiClient.defaults.adapter = async config => ({ config, data: await handler(config), status: 200, statusText: '', headers: {} }) }
const item = { notificationId: 8, type: 'ENROLLMENT_ASSIGNED', title: '새 교육 알림', message: '본인 알림 내용', relatedEnrollmentId: 12, readAt: null, createdAt: '2026-09-30T00:00:00' }
await test('Push API는 기존 Axios로 정확한 경로/메서드/DTO를 전달한다', async () => {
  signIn(); const calls = []; adapter(config => { calls.push(config); return response })
  await pushApi.config(); await pushApi.register({ endpoint: 'https://example.test', p256dh: 'key', auth: 'auth', expirationTime: null }); await pushApi.get(7); await pushApi.disable(7)
  assert.deepEqual(calls.map(c => [c.method, c.url]), [['get', '/push/config'], ['put', '/push/subscriptions'], ['get', '/push/subscriptions/7'], ['delete', '/push/subscriptions/7']])
  assert.deepEqual(Object.keys(JSON.parse(calls[1].data)).sort(), ['auth', 'endpoint', 'expirationTime', 'p256dh'])
  assert.match(calls[1].headers.get('Authorization'), /^Bearer /)
})
await test('로그아웃은 인증 토큰 삭제 이전에 Push 정리와 서버 로그아웃을 수행한다', async () => {
  signIn(); const oldCleanup = pushService.cleanupForLogout; const oldLogout = authApi.logout; const calls = []
  pushService.cleanupForLogout = async () => { assert.ok(authSession.getTokens()); calls.push('push') }
  authApi.logout = async () => { assert.ok(authSession.getTokens()); calls.push('logout') }
  try { await logout(); assert.deepEqual(calls, ['push', 'logout']); assert.equal(authSession.getTokens(), null) }
  finally { pushService.cleanupForLogout = oldCleanup; authApi.logout = oldLogout }
})
await test('로그인 계정 전환은 기존 계정 정리 → 새 계정 구독 격리 → 인증 수락 순서다', async () => {
  signIn(); const oldCleanup = pushService.cleanupForLogout; const oldBefore = pushService.beforeAccount; const oldLogin = authApi.login; const calls = []
  pushService.cleanupForLogout = async () => { assert.equal(authSession.getSnapshot().user.memberId, 1); calls.push('cleanup') }
  authApi.login = async () => { calls.push('login'); return tokens(2) }
  pushService.beforeAccount = async id => { assert.equal(id, 2); assert.equal(authSession.getSnapshot().user, null); calls.push('isolate') }
  try { await loginAction({ email: 'test@example.com', password: 'password' }); assert.deepEqual(calls, ['cleanup', 'login', 'isolate']); assert.equal(authSession.getSnapshot().user.memberId, 2) }
  finally { pushService.cleanupForLogout = oldCleanup; pushService.beforeAccount = oldBefore; authApi.login = oldLogin }
})
await test('Notification 로그인 복귀 경로는 내부 숫자 ID만 허용한다', () => {
  assert.equal(notificationReturnPath('/notifications/8'), '/notifications/8')
  for (const value of ['https://evil.test', '//evil.test', '/admin/learning/1', '/notifications/8?redirect=https://evil.test', '/notifications/../1', '/notifications/0', '/notifications/9007199254740992', null])
    assert.equal(notificationReturnPath(value), null)
})
await test('EMPLOYEE는 단건 조회 성공 후 읽음 처리하고 기존 교육 상세로 이동한다', async () => {
  signIn(); const calls = []; adapter(config => { calls.push([config.method, config.url]); return config.method === 'get' ? item : { ...item, readAt: '2026-10-01T00:00:00' } })
  const entry = createNotificationEntry('8'); assert.equal(entry.getSnapshot().loading, true); await entry.load()
  assert.deepEqual(calls, [['get', '/notifications/8'], ['patch', '/notifications/8/read']])
  assert.equal(entry.getSnapshot().destination, '/employee/learning/12'); assert.equal(entry.getSnapshot().loading, false)
})
await test('ADMIN은 알림 상세를 표시하며 존재하지 않는 교육 경로를 만들지 않는다', async () => {
  signIn(1, ['EMPLOYEE', 'ADMIN']); adapter(() => ({ ...item, readAt: '2026-10-01T00:00:00' }))
  const entry = createNotificationEntry('8'); await entry.load()
  assert.equal(entry.getSnapshot().destination, null); assert.equal(entry.getSnapshot().item.title, item.title)
})
await test('404/타인 알림에는 읽음 요청이나 데이터 노출 없이 오류를 표시한다', async () => {
  signIn(); const calls = []; adapter(config => { calls.push(config.method); throw new AxiosError('private', 'ERR_BAD_REQUEST', config, null, { status: 404, data: { secret: 'hidden' }, headers: {}, config }) })
  const entry = createNotificationEntry('8'); await entry.load()
  assert.deepEqual(calls, ['get']); assert.match(entry.getSnapshot().error, /현재 계정/); assert.equal(entry.getSnapshot().item, null)
})
await test('알림 조회 중 계정이 바뀌면 이전 알림을 표시하거나 읽음 처리하지 않는다', async () => {
  signIn(); const gate = deferred(); const calls = []; adapter(async config => { calls.push(config.method); await gate.promise; return item })
  const entry = createNotificationEntry('8'); const pending = entry.load(); await tick(); signIn(2); gate.resolve(); await pending
  assert.deepEqual(calls, ['get']); assert.equal(entry.getSnapshot().item, null)
})
await test('비로그인/잘못된 ID는 단건 요청하지 않고 조회 실패는 재시도로 복구한다', async () => {
  authSession.clear(); adapter(() => assert.fail('anonymous request')); await createNotificationEntry('8').load()
  signIn(); const invalid = createNotificationEntry('../8'); await invalid.load(); assert.match(invalid.getSnapshot().error, /올바르지/)
  adapter(() => { throw Error('private') }); const entry = createNotificationEntry('8'); await entry.load(); assert.match(entry.getSnapshot().error, /다시 시도/)
  adapter(() => ({ ...item, readAt: '2026-10-01T00:00:00' })); await entry.load(); assert.equal(entry.getSnapshot().error, '')
})
await test('SW postMessage는 닫힌 Bell에서도 서버 count와 목록을 재조회하며 표시 오류를 보존한다', async () => {
  signIn(); const calls = []; adapter(config => { calls.push(config.url); return config.url.endsWith('unread-count') ? { count: 17 } : { content: [item] } })
  const worker = new EventTarget(); const windowObject = new EventTarget()
  Object.defineProperty(navigator, 'serviceWorker', { configurable: true, value: worker })
  Object.defineProperty(globalThis, 'window', { configurable: true, value: windowObject })
  const store = createNotificationStore(); const stop = listenForNotificationChanges(() => void store.refreshFromPush(), store.pushDisplayFailed)
  try {
    worker.dispatchEvent(new MessageEvent('message', { data: { type: 'OTHER' } })); assert.equal(calls.length, 0)
    worker.dispatchEvent(new MessageEvent('message', { data: { type: 'LEARNFLOW_PUSH_DISPLAY_FAILED' } }))
    worker.dispatchEvent(new MessageEvent('message', { data: { type: 'LEARNFLOW_NOTIFICATIONS_CHANGED', count: 999 } })); await tick()
    assert.equal(store.getSnapshot().count, 17); assert.deepEqual(store.getSnapshot().items, [item]); assert.equal(store.getSnapshot().open, false)
    assert.match(store.getSnapshot().pushError, /알림 표시가 실패/); assert.equal(calls.length, 2)
    signalNotificationChanges(); await tick(); assert.equal(calls.length, 4)
    stop(); worker.dispatchEvent(new MessageEvent('message', { data: { type: 'LEARNFLOW_NOTIFICATIONS_CHANGED' } })); await tick(); assert.equal(calls.length, 4)
  } finally { stop(); delete navigator.serviceWorker; delete globalThis.window }
})
await test('읽음 요청 중 도착한 Push도 완료 후 목록과 count를 재조회한다', async () => {
  signIn(); const gate = deferred(); const calls = []
  adapter(async config => { calls.push(config.method); if (config.method === 'patch') { await gate.promise; return { ...item, readAt: 'now' } } return config.url.endsWith('unread-count') ? { count: 5 } : { content: [item] } })
  const store = createNotificationStore(); const pending = store.read(item); await store.refreshFromPush(); gate.resolve(); await pending; await tick()
  assert.deepEqual(calls, ['patch', 'get', 'get']); assert.equal(store.getSnapshot().count, 5)
})

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
try {
  const { PushSettingsView } = await server.ssrLoadModule('/src/push/PushSettings.tsx')
  const { NotificationEntryView } = await server.ssrLoadModule('/src/pages/NotificationEntryPage.tsx')
  await test('Push UI는 권한/지원 상태와 명시적인 켜기/끄기 및 오류를 표시한다', () => {
    for (const [status, text] of [['unsupported', '미지원 환경'], ['default', '알림 권한 미결정'], ['off', 'Push 꺼짐'], ['on', 'Push 켜짐'], ['denied', '사이트 설정'], ['error', '알림 설정 미완료']]) {
      const html = renderToStaticMarkup(h(PushSettingsView, { state: { status, busy: false, error: status === 'error' ? '다시 시도' : '', canEnable: status === 'default' || status === 'off', canDisable: status === 'on' }, enable() {}, disable() {}, retry() {} }))
      assert.ok(html.includes(text)); assert.match(html, /홈 화면/)
      if (status === 'on') assert.match(html, /알림 끄기/)
      if (status === 'default') assert.match(html, /알림 켜기/)
      if (status === 'error') assert.match(html, /role="alert"/)
    }
  })
  await test('Notification 단건 Loading/Error/관리자 내용을 렌더링한다', () => {
    const render = state => renderToStaticMarkup(h(NotificationEntryView, { state: { loading: false, error: '', item: null, destination: null, ...state }, retry() {} }))
    assert.match(render({ loading: true }), /불러오|로딩|loading|확인/i)
    assert.match(render({ error: '알림 조회 실패' }), /알림 조회 실패/)
    assert.ok(render({ item }).includes(item.message))
  })
} finally { await server.close(); authSession.clear() }
