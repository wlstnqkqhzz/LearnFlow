import { Icon } from '../common/Icon'
import { useAuth } from '../../auth/AuthContext'
import { userLabel } from '../../auth/tokenUtils'
import { LogoutButton } from './LogoutButton'
import { NotificationBell } from '../notification/NotificationBell'

export function AppHeader({ title, subtitle }: { title: string; subtitle?: string }) {
  const { user } = useAuth()
  const label = userLabel(user)
  return <header className="app-header"><div className="page-heading"><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div><div className="header-tools">
    <label className="header-search"><Icon name="search" /><span className="sr-only">교육·직원 검색 (준비 중)</span><input type="search" placeholder="교육·직원 검색" disabled title="검색 준비 중" /></label>
    <NotificationBell />
    <span className="avatar" aria-label={`${label} ${user?.email ?? ''}`} title={user?.email}>{label[0]}</span>
    <span className="compact-logout"><LogoutButton compact /></span>
  </div></header>
}
