// 수동 UI 테스트 전용 진입점. 운영 App/빌드에서 import하지 않는다.
// 메모리 Router/가짜 Axios 응답만 사용하며 실제 서버·토큰 저장소는 사용하지 않는다.
import { createRoot } from 'react-dom/client'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { AxiosError } from 'axios'
import { AuthContext } from '../src/auth/AuthContext'
import { RoleRoute } from '../src/routes/RoleRoute'
import { AppLayout } from '../src/components/layout/AppLayout'
import { DepartmentsPage } from '../src/pages/admin/DepartmentsPage'
import { JobPositionsPage } from '../src/pages/admin/JobPositionsPage'
import { MembersPage } from '../src/pages/admin/MembersPage'
import { MemberDetailPage } from '../src/pages/admin/MemberDetailPage'
import { apiClient } from '../src/api/client'
import type { Department, JobPosition, Member, MemberStatus } from '../src/api/adminTypes'
import type { Role } from '../src/auth/authTypes'
import '../src/index.css'
import '../src/App.css'
import '../src/auth.css'
import '../src/admin.css'

const timestamps = { createdAt: '2026-09-21T00:00:00', updatedAt: '2026-09-21T00:00:00' }
const departments: Department[] = [
  { id: 1, code: 'DEV', name: '개발본부', parentDepartmentId: null, isActive: true, ...timestamps },
  { id: 2, code: 'BACKEND', name: '백엔드팀', parentDepartmentId: 1, isActive: true, ...timestamps },
  { id: 3, code: 'OLD', name: '구 개발팀', parentDepartmentId: null, isActive: false, ...timestamps },
]
const jobs: JobPosition[] = [
  { id: 1, code: 'ENGINEER', name: '개발자', isActive: true, ...timestamps },
  { id: 2, code: 'LEGACY', name: '구 직무', isActive: false, ...timestamps },
]
const members: Member[] = [
  { id: 1, employeeNumber: 'TEST001', name: '테스트직원', email: 'test@example.invalid', departmentId: 3, jobPositionId: 2, status: 'ACTIVE', hireDate: '2026-09-01', resignedAt: null, roles: ['EMPLOYEE', 'INSTRUCTOR'], ...timestamps },
  { id: 2, employeeNumber: 'TEST002', name: '퇴사직원', email: 'retired@example.invalid', departmentId: 1, jobPositionId: 1, status: 'RESIGNED', hireDate: '2025-01-01', resignedAt: '2026-09-20T00:00:00', roles: ['EMPLOYEE'], ...timestamps },
]
let nextId = 10
apiClient.defaults.adapter = async config => {
  await new Promise(resolve => setTimeout(resolve, 100))
  const [resource, idText, operation, role] = (config.url ?? '').split('/').filter(Boolean)
  const id = Number(idText)
  const input: Record<string, unknown> = config.data ? JSON.parse(config.data) : {}
  const fail = (status: number, code: string): never => { throw new AxiosError('테스트 오류', undefined, config, undefined, { config, data: { code }, status, statusText: '', headers: {} }) }
  let data: unknown
  if (resource === 'departments' || resource === 'job-positions') {
    const items = resource === 'departments' ? departments : jobs
    if (config.method === 'get') data = config.params?.active ? items.filter(item => item.isActive) : [...items]
    else if (config.method === 'post') {
      if (items.some(item => item.code === input.code)) fail(409, resource === 'departments' ? 'DUPLICATE_DEPARTMENT_CODE' : 'DUPLICATE_JOB_POSITION_CODE')
      const created = { id: nextId++, code: String(input.code), name: String(input.name), isActive: true, ...timestamps }
      if (resource === 'departments') { const item = { ...created, parentDepartmentId: input.parentDepartmentId as number | null }; departments.push(item); data = item }
      else { jobs.push(created); data = created }
    } else {
      const item = items.find(value => value.id === id)
      if (!item) return fail(404, 'RESOURCE_NOT_FOUND')
      if (operation === 'status') item.isActive = Boolean(input.active)
      else Object.assign(item, input)
      data = { ...item }
    }
  } else if (resource === 'members') {
    if (config.method === 'get' && !idText) {
      const params = config.params ?? {}
      const filtered = members.filter(member => (!params.name || member.name.includes(params.name)) && (!params.status || member.status === params.status) && (!params.departmentId || member.departmentId === params.departmentId) && (!params.jobPositionId || member.jobPositionId === params.jobPositionId))
      data = { content: filtered.slice(params.page * params.size, (params.page + 1) * params.size), page: params.page, size: params.size, totalElements: filtered.length, totalPages: Math.ceil(filtered.length / params.size) }
    } else if (config.method === 'post' && !idText) {
      // 비밀번호 원문은 가짜 응답/메모리 회원 데이터에도 보존하지 않는다.
      const member: Member = { id: nextId++, employeeNumber: String(input.employeeNumber), name: String(input.name), email: String(input.email), departmentId: Number(input.departmentId), jobPositionId: Number(input.jobPositionId), hireDate: String(input.hireDate), status: 'ACTIVE', roles: ['EMPLOYEE'], resignedAt: null, ...timestamps }
      members.push(member); data = member
    } else {
      const member = members.find(value => value.id === id)
      if (!member) return fail(404, 'MEMBER_NOT_FOUND')
      if (config.method !== 'get') {
        if (operation === 'roles') member.roles = config.method === 'post' ? [...new Set([...member.roles, role as Role])] : member.roles.filter(value => value !== role)
        else if (operation === 'status') { member.status = input.status as MemberStatus; member.resignedAt = member.status === 'RESIGNED' ? timestamps.createdAt : null }
        else Object.assign(member, input)
      }
      data = { ...member, roles: [...member.roles] }
    }
  } else return fail(404, 'RESOURCE_NOT_FOUND')
  return { config, data, status: 200, statusText: '', headers: {} }
}
const user = { memberId: 999, email: 'preview@example.invalid', roles: ['EMPLOYEE', 'ADMIN'] as Role[] }
createRoot(document.getElementById('root')!).render(
  <MemoryRouter initialEntries={['/admin/departments']}><AuthContext.Provider value={{ user, isAuthenticated: true, isInitializing: false, login: async () => user, logout: async () => {} }}>
    <p style={{ padding: 6, background: '#fff4cc', textAlign: 'center' }}>테스트 전용 · 가짜 응답 · 실제 Backend/저장소 미사용</p>
    <Routes><Route element={<RoleRoute role="ADMIN" />}>
      <Route path="/admin/departments" element={<AppLayout title="조직 관리"><DepartmentsPage /></AppLayout>} />
      <Route path="/admin/job-positions" element={<AppLayout title="직무 관리"><JobPositionsPage /></AppLayout>} />
      <Route path="/admin/members" element={<AppLayout title="회원 관리"><MembersPage /></AppLayout>} />
      <Route path="/admin/members/:memberId" element={<AppLayout title="회원 상세"><MemberDetailPage /></AppLayout>} />
      <Route path="*" element={<p>테스트 대상이 아닌 경로입니다.</p>} />
    </Route></Routes>
  </AuthContext.Provider></MemoryRouter>,
)
