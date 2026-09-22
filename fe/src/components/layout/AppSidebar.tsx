import { Icon, type IconName } from '../common/Icon'
import { useAuth } from '../../auth/AuthContext'
import { userLabel } from '../../auth/tokenUtils'
import { LogoutButton } from './LogoutButton'
import { Link, NavLink } from 'react-router-dom'

const menu: { label: string; icon: IconName; to?: string }[] = [
  { label: '대시보드', icon: 'dashboard', to: '/admin/dashboard' }, { label: '회원 관리', icon: 'members', to: '/admin/members' },
  { label: '조직 관리', icon: 'organization', to: '/admin/departments' }, { label: '직무 관리', icon: 'briefcase', to: '/admin/job-positions' },
  { label: '교육과정', icon: 'book', to: '/admin/courses' }, { label: '교육 배정', icon: 'clipboard', to: '/admin/assignments' },
  { label: '수강 현황', icon: 'chart', to: '/admin/enrollments' }, { label: '시험 관리', icon: 'exam', to: '/admin/exams' },
]

export function AppSidebar() {
  const { user } = useAuth()
  const label = userLabel(user)
  return <aside className="sidebar">
    <Link to="/admin/dashboard" className="brand" aria-label="LearnFlow 대시보드"><span className="brand-symbol">LF</span><span className="brand-copy"><strong>LearnFlow</strong><small>기업 러닝 플랫폼</small></span></Link>
    <nav aria-label="관리자 메뉴"><p className="nav-caption">메뉴</p><ul>{menu.map(({ label, icon, to }) => <li key={icon}>
      {to ? <NavLink className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`} to={to} aria-label={label}><Icon name={icon} /><span>{label}</span></NavLink>
        : <button className="nav-item" type="button" disabled title={`${label} — 준비 중`} aria-label={`${label} (준비 중)`}><Icon name={icon} /><span>{label}</span></button>}
    </li>)}</ul></nav>
    <div className="sidebar-user"><span className="avatar avatar-soft" aria-hidden="true">{label[0]}</span><div className="user-copy"><strong>{label}</strong><small title={user?.email}>{user?.email}</small></div><LogoutButton compact /></div>
  </aside>
}
