import { AppLayout } from './components/layout/AppLayout'
import { DashboardPage } from './pages/DashboardPage'
import './App.css'
import './auth.css'
import './admin.css'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { ProtectedRoute } from './routes/ProtectedRoute'
import { RoleRoute } from './routes/RoleRoute'
import { HomeRedirect } from './routes/HomeRedirect'
import { LoginPage } from './pages/auth/LoginPage'
import { ForbiddenPage } from './pages/auth/ForbiddenPage'
import { PlaceholderPage } from './pages/auth/PlaceholderPage'
import { NotFoundPage } from './pages/auth/NotFoundPage'
import { DepartmentsPage } from './pages/admin/DepartmentsPage'
import { JobPositionsPage } from './pages/admin/JobPositionsPage'
import { MembersPage } from './pages/admin/MembersPage'
import { MemberDetailPage } from './pages/admin/MemberDetailPage'
import { CoursesPage } from './pages/admin/CoursesPage'
import { CourseCreatePage } from './pages/admin/CourseCreatePage'
import { CourseDetailPage } from './pages/admin/CourseDetailPage'

export default function App() {
  return (
    <BrowserRouter><AuthProvider><Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<RoleRoute role="ADMIN" />}>
          <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />
          <Route path="/admin/dashboard" element={<AppLayout><DashboardPage /></AppLayout>} />
          <Route path="/admin/departments" element={<AppLayout title="조직 관리" subtitle="부서의 계층과 활성 상태를 관리합니다"><DepartmentsPage /></AppLayout>} />
          <Route path="/admin/job-positions" element={<AppLayout title="직무 관리" subtitle="조직에서 사용하는 직무를 관리합니다"><JobPositionsPage /></AppLayout>} />
          <Route path="/admin/members" element={<AppLayout title="회원 관리" subtitle="조직 구성원과 역할을 관리합니다"><MembersPage /></AppLayout>} />
          <Route path="/admin/members/:memberId" element={<AppLayout title="회원 상세" subtitle="회원 정보와 재직 상태를 관리합니다"><MemberDetailPage /></AppLayout>} />
          <Route path="/admin/courses" element={<AppLayout title="교육과정" subtitle="교육과정을 생성하고 운영 상태를 관리합니다"><CoursesPage /></AppLayout>} />
          <Route path="/admin/courses/new" element={<AppLayout title="교육과정 만들기" subtitle="교육과정 기본 정보를 입력합니다"><CourseCreatePage /></AppLayout>} />
          <Route path="/admin/courses/:courseId" element={<AppLayout title="교육과정 상세" subtitle="기본 정보와 콘텐츠를 관리합니다"><CourseDetailPage /></AppLayout>} />
        </Route>
        <Route element={<RoleRoute role="EMPLOYEE" />}><Route path="/employee" element={<PlaceholderPage title="직원 학습 공간" />} /></Route>
        <Route element={<RoleRoute role="INSTRUCTOR" />}><Route path="/instructor" element={<PlaceholderPage title="강사 교육 공간" />} /></Route>
        <Route path="/403" element={<ForbiddenPage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes></AuthProvider></BrowserRouter>
  )
}
