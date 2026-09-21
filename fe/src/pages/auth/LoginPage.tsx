import { useRef, useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { homePath } from '../../auth/tokenUtils'
import { apiErrorMessage } from '../../api/apiError'
import { AuthLoading } from './AuthLoading'

export function LoginPage() {
  const { user, isInitializing, login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState('')
  const submitting = useRef(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current) return
    submitting.current = true
    setIsSubmitting(true)
    setError('')
    try { await login({ email, password }) }
    catch (cause) { setError(apiErrorMessage(cause, true)) }
    finally { setPassword(''); setIsSubmitting(false); submitting.current = false }
  }

  if (isInitializing) return <AuthLoading />
  if (user) return <Navigate to={homePath(user)} replace />
  return (
    <main className="auth-screen">
      <div className="login-layout">
        <section className="login-brand" aria-label="LearnFlow 소개">
          <div className="brand login-logo"><span className="brand-symbol">LF</span><span className="brand-copy"><strong>LearnFlow</strong><small>기업 러닝 플랫폼</small></span></div>
          <h1>배움이 성장으로 이어지는 곳</h1>
          <p>우리 조직의 교육과 성장을<br />LearnFlow에서 함께하세요.</p>
        </section>
        <section className="login-card surface" aria-labelledby="login-title">
          <h2 id="login-title">로그인</h2>
          <p className="auth-description">회사에서 발급받은 계정으로 로그인하세요.</p>
          <form onSubmit={submit} aria-busy={isSubmitting}>
            <div className="auth-field"><label htmlFor="email">이메일</label><input id="email" name="email" type="email" autoComplete="username" autoCapitalize="none" spellCheck={false} required maxLength={255} value={email} onChange={event => setEmail(event.target.value)} placeholder="name@company.com" disabled={isSubmitting} /></div>
            <div className="auth-field"><label htmlFor="password">비밀번호</label><input id="password" name="password" type="password" autoComplete="current-password" required maxLength={128} value={password} onChange={event => setPassword(event.target.value)} placeholder="비밀번호를 입력하세요" disabled={isSubmitting} aria-describedby={error ? 'login-error' : undefined} /></div>
            {error && <p className="auth-error" id="login-error" role="alert">{error}</p>}
            <button className="auth-button primary-button" type="submit" aria-label={isSubmitting ? '로그인 중' : '로그인'} disabled={isSubmitting || !email.trim() || !password}>{isSubmitting ? '로그인 중…' : '로그인'}</button>
            {isSubmitting && <p className="sr-only" role="status">로그인 중입니다.</p>}
          </form>
          <p className="login-help">계정 이용에 어려움이 있다면 사내 교육 담당자에게 문의해 주세요.</p>
        </section>
      </div>
    </main>
  )
}
