import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { notificationReturnPath } from '../components/notification/notificationEntry.ts'
import { useAuth } from '../auth/AuthContext'
import { AuthLoading } from '../pages/auth/AuthLoading'

export function ProtectedRoute() {
  const { isInitializing, isAuthenticated } = useAuth()
  const location = useLocation()
  if (isInitializing) return <AuthLoading />
  const returnTo = notificationReturnPath(location.pathname)
  return isAuthenticated ? <Outlet /> : <Navigate to={returnTo ? `/login?notification=${encodeURIComponent(returnTo)}` : '/login'} replace />
}
