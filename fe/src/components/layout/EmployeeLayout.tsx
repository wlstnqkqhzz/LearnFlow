import type { ReactNode } from 'react'
import { NotificationBell } from '../notification/NotificationBell.tsx'
import { useAuth } from '../../auth/AuthContext.ts'
import { LogoutButton } from './LogoutButton.tsx'
import { EmployeeSidebar } from './EmployeeSidebar.tsx'

export function EmployeeLayout({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) {
  const { user } = useAuth()
  const label = '직원'
  return <><a className="skip-link" href="#main-content">본문으로 건너뛰기</a><div className="app-shell"><EmployeeSidebar /><div className="main-area"><header className="app-header employee-header"><div className="page-heading"><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div><div className="header-tools"><NotificationBell /><span className="employee-header-user"><span className="avatar" aria-hidden="true">{label[0]}</span><span><strong>{label}</strong><small>{user?.email}</small></span></span><span className="compact-logout"><LogoutButton compact /></span></div></header><main id="main-content" tabIndex={-1}>{children}</main></div></div></>
}
