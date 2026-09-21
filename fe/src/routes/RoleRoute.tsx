import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import type { Role } from '../auth/authTypes'

// UX용 접근 제어. 실제 권한은 모든 Backend API에서 별도로 검증한다.
export function RoleRoute({ role }: { role: Role }) {
  const { user } = useAuth()
  return user?.roles.includes(role) ? <Outlet /> : <Navigate to="/403" replace />
}
