import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { AuthLoading } from '../pages/auth/AuthLoading'

export function ProtectedRoute() {
  const { isInitializing, isAuthenticated } = useAuth()
  if (isInitializing) return <AuthLoading />
  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />
}
