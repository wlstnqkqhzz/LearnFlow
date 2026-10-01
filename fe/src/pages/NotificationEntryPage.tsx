import { useEffect, useState, useSyncExternalStore } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.ts'
import { authSession } from '../auth/authSession.ts'
import { homePath } from '../auth/tokenUtils.ts'
import { AppHeader } from '../components/layout/AppHeader.tsx'
import { LoadingState, ErrorState } from '../components/admin/AdminUI.tsx'
import { createNotificationEntry, type NotificationEntryState } from '../components/notification/notificationEntry.ts'

export function NotificationEntryPage() {
  const { notificationId } = useParams()
  const { user } = useAuth()
  return <NotificationEntry key={`${notificationId}:${user?.memberId}:${authSession.getGeneration()}`} id={notificationId} />
}
function NotificationEntry({ id }: { id: string | undefined }) {
  const [store] = useState(() => createNotificationEntry(id))
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot)
  const { user } = useAuth()
  useEffect(() => { void store.load(); return () => store.cancel() }, [store])
  if (state.destination) return <Navigate to={state.destination} replace />
  return <><AppHeader title="내 알림" /><main className="surface notification-detail">
    <NotificationEntryView state={state} retry={() => void store.load()} />
    <Link className="admin-button" to={user ? homePath(user) : '/login'}>내 화면으로</Link>
  </main></>
}
export function NotificationEntryView({ state, retry }: { state: NotificationEntryState; retry: () => void }) {
  if (state.loading) return <LoadingState />
  if (state.error) return <ErrorState message={state.error} retry={retry} />
  return state.item ? <article><h2>{state.item.title}</h2><p>{state.item.message}</p><p>읽음 처리되었습니다. 상단 알림에서 다른 알림도 확인할 수 있습니다.</p></article> : null
}
