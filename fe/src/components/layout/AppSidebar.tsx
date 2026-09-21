import { Icon, type IconName } from '../common/Icon'
import { useAuth } from '../../auth/AuthContext'
import { userLabel } from '../../auth/tokenUtils'
import { LogoutButton } from './LogoutButton'

const menu: { label: string; icon: IconName }[] = [
  { label: '대시보드', icon: 'dashboard' }, { label: '회원 관리', icon: 'members' },
  { label: '조직 관리', icon: 'organization' }, { label: '직무 관리', icon: 'briefcase' },
  { label: '교육과정', icon: 'book' }, { label: '교육 배정', icon: 'clipboard' },
  { label: '수강 현황', icon: 'chart' }, { label: '시험 관리', icon: 'exam' },
]

export function AppSidebar() {
  const { user } = useAuth()
  const label = userLabel(user)
  return <aside className="sidebar">
    <a href="#main-content" className="brand" aria-label="LearnFlow 대시보드"><span className="brand-symbol">LF</span><span className="brand-copy"><strong>LearnFlow</strong><small>기업 러닝 플랫폼</small></span></a>
    <nav aria-label="관리자 메뉴"><p className="nav-caption">메뉴</p><ul>{menu.map(({ label, icon }, index) => <li key={icon}>
      {index === 0 ? <a className="nav-item active" href="#main-content" aria-current="page" aria-label={label}><Icon name={icon} /><span>{label}</span></a>
        : <button className="nav-item" type="button" disabled title={`${label} — 준비 중`} aria-label={`${label} (준비 중)`}><Icon name={icon} /><span>{label}</span></button>}
    </li>)}</ul></nav>
    <div className="sidebar-user"><span className="avatar avatar-soft" aria-hidden="true">{label[0]}</span><div className="user-copy"><strong>{label}</strong><small title={user?.email}>{user?.email}</small></div><LogoutButton compact /></div>
  </aside>
}
