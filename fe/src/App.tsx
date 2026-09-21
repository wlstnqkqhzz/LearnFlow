import { AppLayout } from './components/layout/AppLayout'
import { DashboardPage } from './pages/DashboardPage'
import './App.css'
import './auth.css'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { ProtectedRoute } from './routes/ProtectedRoute'
import { RoleRoute } from './routes/RoleRoute'
import { HomeRedirect } from './routes/HomeRedirect'
import { LoginPage } from './pages/auth/LoginPage'
import { ForbiddenPage } from './pages/auth/ForbiddenPage'
import { PlaceholderPage } from './pages/auth/PlaceholderPage'
import { NotFoundPage } from './pages/auth/NotFoundPage'

export default function App() {
  return (
    <BrowserRouter><AuthProvider><Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<RoleRoute role="ADMIN" />}>
          <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />
          <Route path="/admin/dashboard" element={<AppLayout><DashboardPage /></AppLayout>} />
        </Route>
        <Route element={<RoleRoute role="EMPLOYEE" />}><Route path="/employee" element={<PlaceholderPage title="직원 학습 공간" />} /></Route>
        <Route element={<RoleRoute role="INSTRUCTOR" />}><Route path="/instructor" element={<PlaceholderPage title="강사 교육 공간" />} /></Route>
        <Route path="/403" element={<ForbiddenPage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes></AuthProvider></BrowserRouter>
  )
}
