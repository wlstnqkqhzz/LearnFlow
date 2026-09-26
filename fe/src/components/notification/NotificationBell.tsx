import { useEffect, useId, useRef, useState, useSyncExternalStore } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext.ts'
import { authSession } from '../../auth/authSession.ts'
import type { Notification, NotificationType } from '../../api/notificationApi.ts'
import { Icon } from '../common/Icon.tsx'
import { createNotificationStore, notificationPath } from './notificationStore.ts'
import './notification.css'

const labels: Record<NotificationType, string> = {
  ENROLLMENT_ASSIGNED: '교육 배정', COURSE_COMPLETED: '교육 수료', COURSE_FAILED: '교육 실패', ENROLLMENT_EXPIRED: '수강 만료',
}
function time(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(`${value}Z`))
}

export function NotificationBell() {
  const { user } = useAuth()
  // 로그인 전 API 호출 없음. 계정/로그인 세대가 달라지면 패널과 목록도 새로 생성한다.
  return user ? <SignedInBell key={`${user.memberId}:${authSession.getGeneration()}`} employee={user.roles.includes('EMPLOYEE')} /> : null
}

function SignedInBell({ employee }: { employee: boolean }) {
  const [store] = useState(createNotificationStore)
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot)
  const open = state.open
  const panelId = useId()
  const root = useRef<HTMLDivElement>(null)
  const panel = useRef<HTMLElement>(null)
  const bell = useRef<HTMLButtonElement>(null)
  const navigate = useNavigate()
  useEffect(() => {
    void store.load()
    const focus = () => { void store.load(store.getSnapshot().open) }
    window.addEventListener('focus', focus)
    return () => { window.removeEventListener('focus', focus); store.cancel() }
  }, [store])
  useEffect(() => {
    if (!open) return
    panel.current?.focus()
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !root.current?.contains(event.target)) store.closePanel()
    }
    document.addEventListener('pointerdown', outside)
    return () => document.removeEventListener('pointerdown', outside)
  }, [open, store])
  function close() { store.closePanel(); bell.current?.focus() }
  async function select(item: Notification) {
    if (!await store.read(item)) return
    close()
    const path = notificationPath(item, employee)
    if (path) navigate(path)
  }
  return <div className="notification-root" ref={root}
    onKeyDown={event => { if (event.key === 'Escape' && open) { event.preventDefault(); close() } }}
    onBlur={event => { if (event.relatedTarget instanceof Node && !event.currentTarget.contains(event.relatedTarget)) store.closePanel() }}>
    <button ref={bell} className="icon-button notification-button" type="button" aria-label={`알림, 읽지 않은 알림 ${state.count}개`}
      aria-expanded={open} aria-controls={open ? panelId : undefined} onClick={() => void store.togglePanel()}>
      <Icon name="bell" /><NotificationBadge count={state.count} />
    </button>
    {open && <section ref={panel} id={panelId} className="notification-panel" aria-label="내 알림" tabIndex={-1}>
      <div className="notification-heading"><strong>알림</strong><button type="button" className="admin-button" disabled={state.busy || state.loading || state.count === 0} onClick={() => void store.readAll()}>모두 읽음</button><button type="button" className="admin-button" onClick={close} aria-label="알림 닫기">닫기</button></div>
      {state.error && <div className="notification-feedback" role="alert"><p>{state.error}</p><button className="admin-button" disabled={state.busy || state.loading} onClick={() => void store.load(true)}>다시 조회</button></div>}
      {state.loading && <p className="notification-feedback" role="status">알림을 불러오는 중…</p>}
      <NotificationList items={state.items} busy={state.busy || state.loading} loading={state.loading || !!state.error} onSelect={item => void select(item)} />
      {state.items.length > 0 && <p className="notification-footer">최근 알림 최대 20개를 표시합니다.</p>}
    </section>}
  </div>
}

export function NotificationBadge({ count }: { count: number }) {
  return count > 0 ? <span className="notification-count" aria-hidden="true">{count > 99 ? '99+' : count}</span> : null
}

export function NotificationList({ items, busy, loading, onSelect }: {
  items: Notification[]; busy: boolean; loading: boolean; onSelect: (item: Notification) => void
}) {
  if (!items.length) return loading ? null : <p className="notification-feedback">새로운 알림이 없습니다.</p>
  return <ul className="notification-list">{items.map(item => <li key={item.notificationId}>
    <button type="button" className={`notification-item ${item.readAt ? 'is-read' : 'is-unread'}`} disabled={busy} onClick={() => onSelect(item)}>
      <span className="notification-meta">{labels[item.type]} · {item.readAt ? '읽음' : '읽지 않음'}</span>
      <strong>{item.title}</strong><span>{item.message}</span><time dateTime={`${item.createdAt}Z`}>{time(item.createdAt)}</time>
    </button>
  </li>)}</ul>
}
