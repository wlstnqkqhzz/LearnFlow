import { Navigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { homePath } from '../auth/tokenUtils'
import { AuthLoading } from '../pages/auth/AuthLoading'

export function HomeRedirect() {
  const { user, isInitializing } = useAuth()
  if (isInitializing) return <AuthLoading />
  return <Navigate to={user ? homePath(user) : '/login'} replace />
}
