import assert from 'node:assert/strict'
import { test } from 'node:test'
import { AxiosError } from 'axios'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'
import { apiClient } from '../src/api/client.ts'
import { departmentApi } from '../src/api/departmentApi.ts'
import { jobPositionApi } from '../src/api/jobPositionApi.ts'
import { memberApi } from '../src/api/memberApi.ts'
import { adminErrorMessage } from '../src/api/adminError.ts'
import { departmentRows, parentCandidates, selectableOrganizations, organizationName, allowedStatuses } from '../src/pages/admin/adminUtils.ts'

const departments = [
  { id: 1, name: '개발본부', code: 'DEV', parentDepartmentId: null, isActive: true },
  { id: 2, name: '백엔드팀', code: 'BE', parentDepartmentId: 1, isActive: true },
  { id: 3, name: '플랫폼팀', code: 'PLATFORM', parentDepartmentId: 2, isActive: true },
  { id: 4, name: '구 조직', code: 'OLD', parentDepartmentId: null, isActive: false },
]
function capture(data) {
  const calls = []
  apiClient.defaults.adapter = async config => { calls.push(config); return { config, data, status: 200, statusText: '', headers: {} } }
  return calls
}
function body(call) { return call.data ? JSON.parse(call.data) : undefined }

await test('부서 전체/활성 목록은 정확한 active 파라미터와 취소 signal 사용', async () => {
  const calls = capture(departments)
  const signal = new AbortController().signal
  assert.equal((await departmentApi.list(false, signal)).length, 4)
  await departmentApi.list(true)
  assert.deepEqual(calls.map(call => call.params), [{ active: false }, { active: true }])
  assert.equal(calls[0].signal, signal)
})
await test('부서 생성 DTO는 코드/이름/nullable 상위 ID', async () => {
  const calls = capture(departments[0])
  await departmentApi.create({ code: 'DEV', name: '개발본부', parentDepartmentId: null })
  assert.equal(calls[0].method, 'post')
  assert.equal(calls[0].url, '/departments')
  assert.deepEqual(body(calls[0]), { code: 'DEV', name: '개발본부', parentDepartmentId: null })
})
await test('부서 수정은 parent 생략/명시적 null을 구분하고 code를 보내지 않는다', async () => {
  const calls = capture(departments[0])
  await departmentApi.update(1, { name: '새 이름' })
  await departmentApi.update(1, { parentDepartmentId: null })
  assert.deepEqual(body(calls[0]), { name: '새 이름' })
  assert.deepEqual(body(calls[1]), { parentDepartmentId: null })
  assert.equal(calls[0].method, 'patch')
})
await test('부서 비활성화는 DELETE가 아니라 PATCH status', async () => {
  const calls = capture(departments[0])
  await departmentApi.status(1, false)
  assert.equal(calls[0].url, '/departments/1/status')
  assert.equal(calls[0].method, 'patch')
  assert.deepEqual(body(calls[0]), { active: false })
})
await test('평면 부서 목록을 계층 순서/깊이로 표시한다', () => {
  assert.deepEqual(departmentRows([departments[2], departments[1], departments[0], departments[3]]).map(row => [row.department.id, row.depth]), [[1, 0], [2, 1], [3, 2], [4, 0]])
})
await test('부모 누락/순환 응답도 중복이나 무한 순회 없이 처리', () => {
  assert.equal(departmentRows([departments[1]]).length, 1)
  const cycle = [{ ...departments[0], parentDepartmentId: 2 }, departments[1]]
  assert.equal(departmentRows(cycle).length, 2)
})
await test('상위 후보에서 자기/자손/비활성 제외, 기존 비활성 상위는 유지', () => {
  assert.deepEqual(parentCandidates(departments, departments[1]).map(item => item.id), [1])
  assert.deepEqual(parentCandidates(departments, { ...departments[1], parentDepartmentId: 4 }).map(item => item.id), [1, 4])
})
await test('직무 목록/생성/수정/비활성화 계약', async () => {
  const calls = capture([])
  await jobPositionApi.list(true)
  await jobPositionApi.create({ code: 'ENGINEER', name: '개발자' })
  await jobPositionApi.update(5, { name: '엔지니어' })
  await jobPositionApi.status(5, false)
  assert.deepEqual(calls.map(call => [call.method, call.url]), [['get', '/job-positions'], ['post', '/job-positions'], ['patch', '/job-positions/5'], ['patch', '/job-positions/5/status']])
  assert.deepEqual(body(calls[2]), { name: '엔지니어' })
  assert.deepEqual(body(calls[3]), { active: false })
})
await test('회원 검색/필터/페이지는 Backend 지원 파라미터만 사용', async () => {
  const page = { content: [], page: 2, size: 20, totalElements: 45, totalPages: 3 }
  const calls = capture(page)
  const params = { name: '김', status: 'ON_LEAVE', departmentId: 2, jobPositionId: 3, page: 2, size: 20 }
  assert.deepEqual(await memberApi.list(params), page)
  assert.deepEqual(calls[0].params, params)
})
await test('회원 생성은 실제 DTO 필드만 전송하며 roles 필드가 없다', async () => {
  const calls = capture({ id: 1 })
  const data = { employeeNumber: 'E001', name: '테스트', email: 'test@example.invalid', password: 'test-only-password', hireDate: '2026-09-21', departmentId: 1, jobPositionId: 2 }
  await memberApi.create(data)
  assert.deepEqual(body(calls[0]), data)
  assert.equal(Object.hasOwn(body(calls[0]), 'roles'), false)
})
await test('회원 기본정보/부서/직무는 각각의 PATCH Endpoint 사용', async () => {
  const calls = capture({ id: 1 })
  await memberApi.update(1, { name: '수정' })
  await memberApi.department(1, 2)
  await memberApi.jobPosition(1, 3)
  assert.deepEqual(calls.map(call => [call.url, body(call)]), [['/members/1', { name: '수정' }], ['/members/1/department', { departmentId: 2 }], ['/members/1/job-position', { jobPositionId: 3 }]])
})
await test('휴직/복직/퇴사는 실제 Enum을 전송', async () => {
  const calls = capture({ id: 1 })
  for (const status of ['ON_LEAVE', 'ACTIVE', 'RESIGNED']) await memberApi.status(1, status)
  assert.deepEqual(calls.map(call => body(call).status), ['ON_LEAVE', 'ACTIVE', 'RESIGNED'])
  assert.ok(calls.every(call => call.url === '/members/1/status' && call.method === 'patch'))
})
await test('퇴사 상태는 전이 선택지가 없고 재직/휴직만 상호 전환', () => {
  assert.deepEqual(allowedStatuses('RESIGNED'), [])
  assert.deepEqual(allowedStatuses('ACTIVE'), ['ON_LEAVE', 'RESIGNED'])
  assert.deepEqual(allowedStatuses('ON_LEAVE'), ['ACTIVE', 'RESIGNED'])
})
await test('역할은 개별 POST/DELETE로 추가 및 제거', async () => {
  const calls = capture({ roles: ['EMPLOYEE', 'INSTRUCTOR'] })
  await memberApi.addRole(1, 'INSTRUCTOR')
  await memberApi.addRole(1, 'ADMIN')
  await memberApi.removeRole(1, 'ADMIN')
  assert.deepEqual(calls.map(call => [call.method, call.url]), [['post', '/members/1/roles/INSTRUCTOR'], ['post', '/members/1/roles/ADMIN'], ['delete', '/members/1/roles/ADMIN']])
})
await test('신규 조직 후보는 활성만, 기존 비활성 값은 표시 유지', () => {
  assert.deepEqual(selectableOrganizations(departments).map(item => item.id), [1, 2, 3])
  assert.deepEqual(selectableOrganizations(departments, 4).map(item => item.id), [1, 2, 3, 4])
  assert.equal(organizationName(departments, 4), '구 조직 (비활성)')
})
await test('400/404/409 업무 오류 매핑은 SQL 원문을 노출하지 않는다', () => {
  const error = (status, code) => new AxiosError('internal', undefined, undefined, undefined, { status, data: { code, message: 'SQL secret stack' } })
  assert.match(adminErrorMessage(error(400, 'DEPARTMENT_CYCLE')), /하위 부서/)
  assert.match(adminErrorMessage(error(404, 'MEMBER_NOT_FOUND')), /회원을 찾을/)
  assert.match(adminErrorMessage(error(409, 'DUPLICATE_EMAIL')), /이미 사용/)
  assert.doesNotMatch(adminErrorMessage(error(500, 'INTERNAL_SERVER_ERROR')), /SQL|stack/)
})
await test('관리 API 오류는 호출자에게 전달된다', async () => {
  apiClient.defaults.adapter = async config => { throw new AxiosError('Test error', undefined, config, undefined, { status: 409, data: { code: 'DUPLICATE_DEPARTMENT_CODE' }, config }) }
  await assert.rejects(departmentApi.create({ code: 'DEV', name: '중복', parentDepartmentId: null }), error => adminErrorMessage(error).includes('부서 코드'))
})

// Vite의 기존 TSX 변환 + React 서버 렌더러로 공통 UI와 실제 RoleRoute를 검증한다.
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
try {
  const ui = await server.ssrLoadModule('/src/components/admin/AdminUI.tsx')
  const { OrganizationSelect } = await server.ssrLoadModule('/src/components/admin/OrganizationSelect.tsx')
  await test('회원 상태 3종과 복수 역할 Badge 렌더링', () => {
    for (const [status, label] of [['ACTIVE', '재직'], ['ON_LEAVE', '휴직'], ['RESIGNED', '퇴사']]) assert.ok(renderToStaticMarkup(h(ui.MemberBadge, { status })).includes(label))
    const html = renderToStaticMarkup(h(ui.RoleBadges, { roles: ['EMPLOYEE', 'INSTRUCTOR', 'ADMIN'] }))
    assert.ok(['직원', '강사', '관리자'].every(label => html.includes(label)))
  })
  await test('선택 UI는 현재 비활성 값을 숨기지 않고 신규 등록에서는 제외', () => {
    const edit = renderToStaticMarkup(h(OrganizationSelect, { label: '부서', name: 'departmentId', items: departments, currentId: 4 }))
    assert.match(edit, /비활성·현재 값/)
    assert.match(edit, /selected="" value="4"|value="4" selected=""/)
    const create = renderToStaticMarkup(h(OrganizationSelect, { label: '부서', name: 'departmentId', items: departments }))
    assert.doesNotMatch(create, /구 조직/)
  })
  await test('Loading/Empty/Error 상태와 성공 피드백 렌더링', () => {
    assert.match(renderToStaticMarkup(h(ui.LoadingState)), /role="status"/)
    assert.match(renderToStaticMarkup(h(ui.EmptyState, { message: '등록된 부서가 없습니다.' })), /등록된 부서/)
    assert.match(renderToStaticMarkup(h(ui.ErrorState, { message: '오류', retry: () => {} })), /다시 시도/)
    assert.match(renderToStaticMarkup(h(ui.Feedback, { success: '저장했습니다.' })), /저장했습니다/)
  })
  const { RoleRoute } = await server.ssrLoadModule('/src/routes/RoleRoute.tsx')
  const { AuthContext } = await server.ssrLoadModule('/src/auth/AuthContext.ts')
  const { MemoryRouter, Routes, Route } = await import('react-router-dom')
  await test('실제 RoleRoute는 ADMIN 허용, EMPLOYEE/INSTRUCTOR 차단', () => {
    for (const roles of [['EMPLOYEE', 'ADMIN'], ['EMPLOYEE'], ['EMPLOYEE', 'INSTRUCTOR']]) {
      const html = renderToStaticMarkup(h(AuthContext.Provider, { value: { user: { roles } } }, h(MemoryRouter, { initialEntries: ['/admin/members'] }, h(Routes, null, h(Route, { element: h(RoleRoute, { role: 'ADMIN' }) }, h(Route, { path: '/admin/members', element: h('p', null, '관리자 화면') }))))))
      assert.equal(html.includes('관리자 화면'), roles.includes('ADMIN'))
    }
  })
} finally { await server.close() }
