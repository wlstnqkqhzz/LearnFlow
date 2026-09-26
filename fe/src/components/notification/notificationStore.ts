import { notificationApi, type Notification } from '../../api/notificationApi.ts'
import { authSession } from '../../auth/authSession.ts'

type State = { items: Notification[]; count: number; loading: boolean; busy: boolean; error: string; open: boolean }
const empty: State = { items: [], count: 0, loading: false, busy: false, error: '', open: false }

// Header 인스턴스의 작은 저장소. 계정 변경 뒤 도착한 응답은 절대 반영하지 않는다.
export function createNotificationStore() {
  const generation = authSession.getGeneration()
  const memberId = authSession.getSnapshot().user?.memberId
  let state: State = empty
  let sequence = 0
  const listeners = new Set<() => void>()
  const active = () => memberId !== undefined && generation === authSession.getGeneration()
    && authSession.getSnapshot().user?.memberId === memberId
  function publish(patch: Partial<State>) {
    if (!active()) return
    state = { ...state, ...patch }
    listeners.forEach(listener => listener())
  }
  async function load(withList = false) {
    if (!active() || state.busy) return
    const request = ++sequence
    publish({ loading: true, error: '' })
    try {
      const [count, page] = await Promise.all([notificationApi.unread(), withList ? notificationApi.list() : Promise.resolve(null)])
      if (request !== sequence) return
      publish({ count: count.count, ...(page ? { items: page.content } : {}) })
    } catch {
      if (request === sequence) publish({ error: '알림을 불러오지 못했습니다. 다시 시도해 주세요.' })
    } finally { if (request === sequence) publish({ loading: false }) }
  }
  async function read(item: Notification) {
    if (!active() || state.busy) return false
    if ((state.items.find(value => value.notificationId === item.notificationId) ?? item).readAt) return true
    const request = ++sequence
    publish({ busy: true, loading: false, error: '' })
    try {
      const updated = await notificationApi.read(item.notificationId)
      if (request !== sequence || !active()) return false
      publish({ items: state.items.map(value => value.notificationId === updated.notificationId ? updated : value),
        count: Math.max(0, state.count - 1) })
      return active()
    } catch {
      if (request === sequence) publish({ error: '읽음 처리에 실패했습니다. 다시 시도해 주세요.' })
      return false
    } finally { publish({ busy: false }) }
  }
  async function readAll() {
    if (!active() || state.busy) return
    ++sequence
    publish({ busy: true, loading: false, error: '' })
    try {
      const result = await notificationApi.readAll()
      // 성공 응답의 서버 시각을 사용하고 이후 새로 도착한 알림도 재조회한다.
      publish({ count: 0, items: state.items.map(item => ({ ...item, readAt: item.readAt ?? result.readAt })), busy: false })
      await load(true)
    } catch { publish({ error: '모두 읽음 처리에 실패했습니다. 다시 시도해 주세요.' }) }
    finally { publish({ busy: false }) }
  }
  return {
    getSnapshot: () => active() ? state : empty,
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener) } },
    load, read, readAll,
    async togglePanel() {
      if (!active()) return
      const open = !state.open
      publish({ open })
      if (open) await load(true)
    },
    closePanel() { publish({ open: false }) },
    cancel() { ++sequence },
  }
}

export function notificationPath(item: Notification, employee: boolean) {
  return employee && item.relatedEnrollmentId != null ? `/employee/learning/${item.relatedEnrollmentId}` : null
}
