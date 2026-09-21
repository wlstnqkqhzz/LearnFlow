import { Icon } from '../common/Icon'
import { useAuth } from '../../auth/AuthContext'
import { userLabel } from '../../auth/tokenUtils'
import { LogoutButton } from './LogoutButton'

export function AppHeader({ title, subtitle }: { title: string; subtitle?: string }) {
  const { user } = useAuth()
  const label = userLabel(user)
  return <header className="app-header"><div className="page-heading"><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div><div className="header-tools">
    <label className="header-search"><Icon name="search" /><span className="sr-only">교육·직원 검색 (준비 중)</span><input type="search" placeholder="교육·직원 검색" disabled title="검색 준비 중" /></label>
    <button className="icon-button notification-button" type="button" disabled aria-label="알림 (준비 중)" title="알림 준비 중"><Icon name="bell" /><span className="notification-dot" /></button>
    <span className="avatar" aria-label={`${label} ${user?.email ?? ''}`} title={user?.email}>{label[0]}</span>
    <span className="compact-logout"><LogoutButton compact /></span>
  </div></header>
}
