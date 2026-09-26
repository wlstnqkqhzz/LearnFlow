import { AppLayout } from './components/layout/AppLayout'
import { DashboardPage } from './pages/DashboardPage'
import './App.css'
import './auth.css'
import './admin.css'
import './employee.css'
import './employee-exam.css'
import { EmployeeExamPage } from './pages/employee/EmployeeExamPage'
import { EmployeeExamResultPage } from './pages/employee/EmployeeExamResultPage'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { ProtectedRoute } from './routes/ProtectedRoute'
import { RoleRoute } from './routes/RoleRoute'
import { HomeRedirect } from './routes/HomeRedirect'
import { LoginPage } from './pages/auth/LoginPage'
import { ForbiddenPage } from './pages/auth/ForbiddenPage'
import { NotFoundPage } from './pages/auth/NotFoundPage'
import { DepartmentsPage } from './pages/admin/DepartmentsPage'
import { JobPositionsPage } from './pages/admin/JobPositionsPage'
import { MembersPage } from './pages/admin/MembersPage'
import { MemberDetailPage } from './pages/admin/MemberDetailPage'
import { CoursesPage } from './pages/admin/CoursesPage'
import { CourseCreatePage } from './pages/admin/CourseCreatePage'
import { CourseDetailPage } from './pages/admin/CourseDetailPage'
import { EnrollmentsPage } from './pages/admin/EnrollmentsPage'
import { ExamsPage } from './pages/admin/ExamsPage'
import { ExamPage } from './pages/admin/ExamPage'
import { EmployeeLayout } from './components/layout/EmployeeLayout'
import { LearningPage } from './pages/employee/LearningPage'
import { LearningDetailPage } from './pages/employee/LearningDetailPage'
import { ContentLearningPage } from './pages/employee/ContentLearningPage'

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
          <Route path="/admin/courses/:courseId" element={<AppLayout title="교육과정 상세" subtitle="기본 정보, 콘텐츠, 배정 규칙과 시험을 관리합니다"><CourseDetailPage /></AppLayout>} />
          <Route path="/admin/assignments" element={<AppLayout title="교육 배정" subtitle="교육과정을 선택해 자동 배정 규칙을 관리합니다"><CoursesPage assignmentMode /></AppLayout>} />
          <Route path="/admin/enrollments" element={<AppLayout title="수강 현황" subtitle="과정별 배정 및 학습 상태를 조회합니다"><EnrollmentsPage /></AppLayout>} />
          <Route path="/admin/exams" element={<AppLayout title="시험 관리" subtitle="과정별 시험과 문항을 관리합니다"><ExamsPage /></AppLayout>} />
          <Route path="/admin/courses/:courseId/exam" element={<AppLayout title="시험 관리" subtitle="시험 기본정보와 문항을 구성합니다"><ExamPage /></AppLayout>} />
        </Route>
        <Route element={<RoleRoute role="EMPLOYEE" />}>
          <Route path="/employee/learning/:enrollmentId/exam/:attemptId" element={<EmployeeLayout title="시험 응시"><EmployeeExamPage /></EmployeeLayout>} />
          <Route path="/employee/learning/:enrollmentId/exam/:attemptId/result" element={<EmployeeLayout title="시험 결과"><EmployeeExamResultPage /></EmployeeLayout>} />
          <Route path="/employee" element={<Navigate to="/employee/learning" replace />} />
          <Route path="/employee/learning" element={<EmployeeLayout title="내 교육" subtitle="배정된 교육을 확인하고 학습을 이어갈 수 있습니다."><LearningPage /></EmployeeLayout>} />
          <Route path="/employee/learning/:enrollmentId" element={<EmployeeLayout title="교육 상세" subtitle="학습 현황과 콘텐츠를 확인합니다."><LearningDetailPage /></EmployeeLayout>} />
          <Route path="/employee/learning/:enrollmentId/content/:contentId" element={<EmployeeLayout title="콘텐츠 학습" subtitle="학습 자료를 열고 진도를 저장합니다."><ContentLearningPage /></EmployeeLayout>} />
        </Route>
        <Route element={<RoleRoute role="INSTRUCTOR" />}><Route path="/instructor" element={<Navigate to="/employee/learning" replace />} /></Route>
        <Route path="/403" element={<ForbiddenPage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes></AuthProvider></BrowserRouter>
  )
}
