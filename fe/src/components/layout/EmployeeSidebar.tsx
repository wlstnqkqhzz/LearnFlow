import { NavLink, Link } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext.ts'
import { Icon } from '../common/Icon.tsx'
import { LogoutButton } from './LogoutButton.tsx'

export function EmployeeSidebar() {
  const { user } = useAuth()
  const label = '직원'
  return <aside className="sidebar employee-sidebar">
    <Link to="/employee/learning" className="brand" aria-label="LearnFlow 내 교육"><span className="brand-symbol">LF</span><span className="brand-copy"><strong>LearnFlow</strong><small>나의 학습</small></span></Link>
    <nav aria-label="직원 메뉴"><p className="nav-caption">메뉴</p><ul><li><NavLink aria-label="내 교육" className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`} to="/employee/learning"><Icon name="book" /><span>내 교육</span></NavLink></li></ul></nav>
    <div className="sidebar-user"><span className="avatar avatar-soft" aria-hidden="true">{label[0]}</span><div className="user-copy"><strong>{label}</strong><small title={user?.email}>{user?.email}</small></div><LogoutButton compact /></div>
  </aside>
}
