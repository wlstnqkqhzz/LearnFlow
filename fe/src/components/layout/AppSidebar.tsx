import { Icon, type IconName } from '../common/Icon'

const menu: { label: string; icon: IconName }[] = [
  { label: '대시보드', icon: 'dashboard' }, { label: '회원 관리', icon: 'members' },
  { label: '조직 관리', icon: 'organization' }, { label: '직무 관리', icon: 'briefcase' },
  { label: '교육과정', icon: 'book' }, { label: '교육 배정', icon: 'clipboard' },
  { label: '수강 현황', icon: 'chart' }, { label: '시험 관리', icon: 'exam' },
]

export function AppSidebar() {
  return <aside className="sidebar">
    <a href="#main-content" className="brand" aria-label="LearnFlow 대시보드"><span className="brand-symbol">LF</span><span className="brand-copy"><strong>LearnFlow</strong><small>기업 러닝 플랫폼</small></span></a>
    <nav aria-label="관리자 메뉴"><p className="nav-caption">메뉴</p><ul>{menu.map(({ label, icon }, index) => <li key={icon}>
      {index === 0 ? <a className="nav-item active" href="#main-content" aria-current="page" aria-label={label}><Icon name={icon} /><span>{label}</span></a>
        : <button className="nav-item" type="button" disabled title={`${label} — 준비 중`} aria-label={`${label} (준비 중)`}><Icon name={icon} /><span>{label}</span></button>}
    </li>)}</ul></nav>
    <div className="sidebar-user"><span className="avatar avatar-soft" aria-hidden="true">관</span><div className="user-copy"><strong>관리자</strong><small title="admin@company.com">admin@company.com</small></div><button className="icon-button" type="button" disabled aria-label="로그아웃 (준비 중)" title="로그아웃 준비 중"><Icon name="logout" /></button></div>
  </aside>
}
