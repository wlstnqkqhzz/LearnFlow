import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return <main className="auth-screen"><section className="message-card surface"><span className="message-code">404</span><h1>페이지를 찾을 수 없습니다.</h1><p>주소를 확인하거나 홈으로 이동해 주세요.</p><div className="auth-actions"><Link className="auth-button primary-button" to="/">홈으로</Link></div></section></main>
}
