import type { ReactNode } from 'react'
import type { MemberStatus } from '../../api/adminTypes'
import type { Role } from '../../auth/authTypes'
import { memberStatuses, roleLabels } from '../../pages/admin/adminUtils'

export function PageHeader({ title, description, children }: { title: string; description: string; children?: ReactNode }) {
  return <div className="management-heading"><div><h2>{title}</h2><p>{description}</p></div>{children}</div>
}
export function Feedback({ error, success }: { error?: string; success?: string }) {
  return <>{error && <p className="admin-error" role="alert">{error}</p>}{success && <p className="admin-success" role="status">{success}</p>}</>
}
export function LoadingState() { return <p className="admin-state" role="status">데이터를 불러오고 있습니다…</p> }
export function ErrorState({ message, retry }: { message: string; retry: () => void }) {
  return <div className="admin-state"><p role="alert">{message}</p><button className="admin-button" onClick={retry} type="button">다시 시도</button></div>
}
export function EmptyState({ message, children }: { message: string; children?: ReactNode }) {
  return <div className="admin-state"><p>{message}</p>{children}</div>
}
export function ActiveBadge({ active }: { active: boolean }) { return <span className={`status-badge tone-${active ? 'success' : 'neutral'}`}>{active ? '활성' : '비활성'}</span> }
export function MemberBadge({ status }: { status: MemberStatus }) { const value = memberStatuses[status]; return <span className={`status-badge tone-${value.tone}`}>{value.label}</span> }
export function RoleBadges({ roles }: { roles: Role[] }) { return <span className="role-badges">{(['EMPLOYEE', 'INSTRUCTOR', 'ADMIN'] as Role[]).filter(role => roles.includes(role)).map(role => <span key={role} className={`status-badge tone-${role === 'ADMIN' ? 'primary' : 'neutral'}`}>{roleLabels[role]}</span>)}</span> }
export function SubmitButton({ pending, label = '저장' }: { pending: boolean; label?: string }) { return <button type="submit" className="admin-button primary-button" disabled={pending}>{pending ? '처리 중…' : label}</button> }
