import { Link, useNavigate } from 'react-router-dom'

export function ForbiddenPage() {
  const navigate = useNavigate()
  return <main className="auth-screen"><section className="message-card surface"><span className="message-code">403</span><h1>접근 권한이 없습니다.</h1><p>현재 계정으로 이 페이지를 이용할 수 없습니다.</p><div className="auth-actions"><button type="button" className="auth-button" onClick={() => navigate(-1)}>이전 화면</button><Link className="auth-button primary-button" to="/">홈으로</Link></div></section></main>
}
