import { useEffect, useSyncExternalStore, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { AuthContext } from './AuthContext'
import { authSession } from './authSession'
import { initializeAuth, login, logout } from './authActions'
import { onForbidden } from '../api/client'

export function AuthProvider({ children }: { children: ReactNode }) {
  const state = useSyncExternalStore(authSession.subscribe, authSession.getSnapshot)
  const navigate = useNavigate()
  useEffect(() => { void initializeAuth() }, [])
  useEffect(() => onForbidden(() => navigate('/403', { replace: true })), [navigate])
  return <AuthContext.Provider value={{ ...state, isAuthenticated: !!state.user, login, logout }}>{children}</AuthContext.Provider>
}
