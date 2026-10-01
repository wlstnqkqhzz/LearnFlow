import axios from 'axios'
import { notificationApi, type Notification } from '../../api/notificationApi.ts'
import { authSession } from '../../auth/authSession.ts'
import { notificationPath } from './notificationStore.ts'
import { signalNotificationChanges } from '../../push/pushMessages.ts'

export function notificationReturnPath(value: unknown): string | null {
  if (typeof value !== 'string' || !/^\/notifications\/[1-9]\d*$/.test(value)) return null
  const id = Number(value.split('/').pop())
  return Number.isSafeInteger(id) ? value : null
}
export type NotificationEntryState = { loading: boolean; error: string; item: Notification | null; destination: string | null }
export function createNotificationEntry(id: string | undefined) {
  let state: NotificationEntryState = { loading: true, error: '', item: null, destination: null }
  const listeners = new Set<() => void>()
  const generation = authSession.getGeneration()
  const user = authSession.getSnapshot().user
  let sequence = 0
  const active = () => !!user && generation === authSession.getGeneration()
  const publish = (patch: Partial<NotificationEntryState>) => { if (active()) { state = { ...state, ...patch }; listeners.forEach(fn => fn()) } }
  return {
    getSnapshot: () => state,
    subscribe(fn: () => void) { listeners.add(fn); return () => { listeners.delete(fn) } },
    cancel() { ++sequence },
    async load() {
      if (!active()) return
      const current = ++sequence
      publish({ loading: true, error: '', item: null, destination: null })
      if (!notificationReturnPath(`/notifications/${id}`)) { publish({ loading: false, error: '올바르지 않은 알림 주소입니다.' }); return }
      try {
        const item = await notificationApi.get(Number(id))
        if (!active() || current !== sequence) return
        const read = item.readAt ? item : await notificationApi.read(item.notificationId)
        if (!active() || current !== sequence) return
        publish({ item: read, destination: notificationPath(read, user!.roles.includes('EMPLOYEE') && !user!.roles.includes('ADMIN')) })
        signalNotificationChanges()
      } catch (error) {
        if (current === sequence) publish({ error: axios.isAxiosError(error) && error.response?.status === 404
          ? '알림을 찾을 수 없거나 현재 계정의 알림이 아닙니다.' : '알림을 확인하지 못했습니다. 다시 시도해 주세요.' })
      } finally { if (current === sequence) publish({ loading: false }) }
    },
  }
}
