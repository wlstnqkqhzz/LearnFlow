import { Icon, type IconName } from '../common/Icon'
import { useAuth } from '../../auth/AuthContext'
import { userLabel } from '../../auth/tokenUtils'
import { LogoutButton } from './LogoutButton'
import { Link, useLocation } from 'react-router-dom'

const menu: { label: string; icon: IconName; to: string }[] = [
  { label: '대시보드', icon: 'dashboard', to: '/admin/dashboard' }, { label: '회원 관리', icon: 'members', to: '/admin/members' },
  { label: '조직 관리', icon: 'organization', to: '/admin/departments' }, { label: '직무 관리', icon: 'briefcase', to: '/admin/job-positions' },
  { label: '교육과정', icon: 'book', to: '/admin/courses' }, { label: '교육 배정', icon: 'clipboard', to: '/admin/assignments' },
  { label: '재교육 관리', icon: 'book', to: '/admin/retraining-policies' },
  { label: '교육 통계', icon: 'chart', to: '/admin/statistics' },
  { label: '수강 현황', icon: 'chart', to: '/admin/enrollments' }, { label: '시험 관리', icon: 'exam', to: '/admin/exams' },
]

export function AppSidebar() {
  const { pathname } = useLocation()
  const examDetail = /^\/admin\/courses\/\d+\/exam\/?$/.test(pathname)
  const { user } = useAuth()
  const instructorView = pathname.startsWith('/instructor')
  const visibleMenu = instructorView ? [
    { label: '담당 과정 / 과제', icon: 'book' as IconName, to: '/instructor/courses' },
    ...(user?.roles.includes('EMPLOYEE') ? [{ label: '내 교육', icon: 'clipboard' as IconName, to: '/employee/learning' }] : []),
    ...(user?.roles.includes('ADMIN') ? [{ label: '관리자 대시보드', icon: 'dashboard' as IconName, to: '/admin/dashboard' }] : []),
  ] : user?.roles.includes('ADMIN') ? menu : []
  const label = userLabel(user)
  return <aside className="sidebar">
    <Link to={instructorView ? '/instructor/courses' : '/admin/dashboard'} className="brand" aria-label={instructorView ? 'LearnFlow 담당 과정' : 'LearnFlow 대시보드'}><span className="brand-symbol">LF</span><span className="brand-copy"><strong>LearnFlow</strong><small>기업 러닝 플랫폼</small></span></Link>
    <nav aria-label={instructorView ? '강사 메뉴' : '관리자 메뉴'}><p className="nav-caption">메뉴</p><ul>{visibleMenu.map(({ label, icon, to }) => <li key={to}>
      <Link className={`nav-item${(examDetail ? to === '/admin/exams' : pathname === to || pathname.startsWith(`${to}/`)) ? ' active' : ''}`} aria-current={(examDetail ? to === '/admin/exams' : pathname === to || pathname.startsWith(`${to}/`)) ? 'page' : undefined} to={to} aria-label={label}><Icon name={icon} /><span>{label}</span></Link>
    </li>)}</ul></nav>
    <div className="sidebar-user"><span className="avatar avatar-soft" aria-hidden="true">{label[0]}</span><div className="user-copy"><strong>{label}</strong><small title={user?.email}>{user?.email}</small></div><LogoutButton compact /></div>
  </aside>
}
