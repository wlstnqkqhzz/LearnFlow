import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { Icon } from '../common/Icon'

export function LogoutButton({ compact = false }: { compact?: boolean }) {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [pending, setPending] = useState(false)
  async function handleLogout() {
    setPending(true)
    try { await logout() }
    finally { setPending(false); navigate('/login', { replace: true }) }
  }
  return <button className={compact ? 'icon-button' : 'auth-button'} type="button" disabled={pending} onClick={() => void handleLogout()} aria-label={pending ? '로그아웃 중' : '로그아웃'} title={pending ? '로그아웃 중' : '로그아웃'}>{compact ? <Icon name="logout" /> : pending ? '로그아웃 중…' : '로그아웃'}</button>
}
