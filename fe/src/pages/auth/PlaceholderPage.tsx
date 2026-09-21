import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { LogoutButton } from '../../components/layout/LogoutButton'

export function PlaceholderPage({ title }: { title: string }) {
  const { user } = useAuth()
  return <main className="auth-screen"><section className="message-card surface"><span className="brand-symbol" aria-hidden="true">LF</span><h1>{title}</h1><p>이 화면은 준비 중입니다.</p><p className="placeholder-email">{user?.email}</p><div className="auth-actions"><Link className="auth-button" to="/">홈으로</Link><LogoutButton /></div></section></main>
}
