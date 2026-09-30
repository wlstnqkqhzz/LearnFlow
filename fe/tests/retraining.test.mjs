import assert from 'node:assert/strict'
import { test } from 'node:test'
import { Children, createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { createServer } from 'vite'
import { AxiosError } from 'axios'
import { apiClient } from '../src/api/client.ts'
import { retrainingApi } from '../src/api/retrainingApi.ts'
import { adminErrorMessage } from '../src/api/adminError.ts'
import { scheduleLocked, policyRequest, policyValidation, occurrenceValidation } from '../src/pages/admin/retrainingUtils.ts'

const request = { sourceCourseId: 7, baseTitle: '보안 교육', enabled: true, autoCreate: true, autoOpen: false, intervalMonths: 12, firstStartDate: '2027-01-31', durationDays: 30, generationLeadDays: 7 }
const policy = { ...request, id: 4, nextOccurrenceNumber: 1, nextGenerationDate: '2027-01-24', createdAt: '2026-09-30T00:00:00', updatedAt: '2026-09-30T00:00:00' }
const course = { id: 80, title: '보안 교육 2027-01 · 1회차', status: 'DRAFT', startDate: '2027-01-31', endDate: '2027-03-01', retrainingPolicyId: 4, occurrenceNumber: 1 }
const page = content => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 })
const noop = () => {}
function response(config, data, status = 200) { return { config, data, status, statusText: '', headers: {} } }
async function withAdapter(adapter, action) {
  const previous = apiClient.defaults.adapter
  apiClient.defaults.adapter = adapter
  try { await action() } finally { apiClient.defaults.adapter = previous }
}

await test('Retraining Controller 8개 API의 실제 메서드/URI/전체 DTO 및 페이지 계약', async () => {
  const calls = [], signal = new AbortController().signal
  await withAdapter(async config => { calls.push(config); return response(config, policy) }, async () => {
    assert.deepEqual(await retrainingApi.list(2, 20, signal), policy)
    await retrainingApi.get(4, signal)
    await retrainingApi.create(request)
    await retrainingApi.update(4, request)
    await retrainingApi.status(4, false)
    await retrainingApi.courses(4, 1, 20, signal)
    await retrainingApi.generate(4, 3)
    await retrainingApi.skip(4, 3)
  })
  assert.deepEqual(calls.map(c => [c.method, c.url]), [
    ['get', '/retraining-policies'], ['get', '/retraining-policies/4'], ['post', '/retraining-policies'],
    ['patch', '/retraining-policies/4'], ['patch', '/retraining-policies/4/status'], ['get', '/retraining-policies/4/courses'],
    ['put', '/retraining-policies/4/occurrences/3'], ['post', '/retraining-policies/4/skip-overdue'],
  ])
  assert.deepEqual(calls[0].params, { page: 2, size: 20 })
  assert.deepEqual(calls[5].params, { page: 1, size: 20 })
  for (const index of [0, 1, 5]) assert.equal(calls[index].signal, signal)
  for (const index of [2, 3]) assert.deepEqual(JSON.parse(calls[index].data), request)
  assert.deepEqual(JSON.parse(calls[4].data), { enabled: false })
  assert.equal(calls[6].data, undefined)
  assert.deepEqual(JSON.parse(calls[7].data), { occurrenceNumber: 3 })
})

await test('최초 생성 201 / 동일 회차 재요청 200 모두 같은 Course로 처리', async () => {
  let count = 0
  await withAdapter(async config => { count++; return response(config, course, count === 1 ? 201 : 200) }, async () => {
    assert.deepEqual(await retrainingApi.generate(4, 1), course)
    assert.deepEqual(await retrainingApi.generate(4, 1), course)
    assert.equal(count, 2)
  })
})

await test('활성/중지 값과 skip 지정 회차 유지, 다음 일정은 서버 응답 그대로 사용', async () => {
  const advanced = { ...policy, nextOccurrenceNumber: 2, nextGenerationDate: '2028-01-24' }
  const calls = []
  await withAdapter(async config => {
    calls.push(config)
    if (config.url.endsWith('/status')) return response(config, { ...policy, enabled: JSON.parse(config.data).enabled })
    return response(config, advanced)
  }, async () => {
    assert.equal((await retrainingApi.status(4, false)).enabled, false)
    assert.equal((await retrainingApi.status(4, true)).enabled, true)
    assert.deepEqual(await retrainingApi.skip(4, 1), advanced)
    assert.deepEqual(await retrainingApi.skip(4, 1), advanced)
    assert.deepEqual(calls.slice(2).map(c => JSON.parse(c.data)), [{ occurrenceNumber: 1 }, { occurrenceNumber: 1 }])
  })
})

await test('생성/skip 이후 일정 잠금은 서버 커서 기준이며 수정 요청은 잠긴 원본 값을 유지', () => {
  assert.equal(scheduleLocked(), false)
  assert.equal(scheduleLocked(policy), false)
  const locked = { ...policy, nextOccurrenceNumber: 2 }
  assert.equal(scheduleLocked(locked), true)
  const edited = { ...request, sourceCourseId: 999, baseTitle: ' 변경 제목 ', intervalMonths: 1, firstStartDate: '2029-01-01', durationDays: 1, generationLeadDays: 0, enabled: false, autoCreate: false, autoOpen: true }
  const result = policyRequest(edited, locked)
  assert.deepEqual(result, { ...request, baseTitle: '변경 제목', enabled: false, autoCreate: false, autoOpen: true })
  assert.equal(Object.hasOwn(result, 'nextOccurrenceNumber'), false)
  assert.deepEqual(policyRequest(edited, policy), { ...edited, baseTitle: '변경 제목' })
})

await test('폼 숫자/제목/날짜 기본 검증과 과거 날짜 허용: 최종 일정 판정은 서버 담당', () => {
  assert.equal(policyValidation(request), '')
  assert.equal(policyValidation({ ...request, firstStartDate: '2000-02-29', generationLeadDays: 0 }), '')
  for (const patch of [
    { sourceCourseId: 0 }, { sourceCourseId: 1.2 }, { baseTitle: ' ' }, { baseTitle: '가'.repeat(151) },
    { intervalMonths: 0 }, { intervalMonths: 1201 }, { intervalMonths: 1.5 }, { durationDays: 0 },
    { durationDays: 3661 }, { generationLeadDays: -1 }, { generationLeadDays: 3661 },
    { firstStartDate: '' }, { firstStartDate: '2027-02-29' }, { firstStartDate: '2027-04-31' }, { firstStartDate: '0999-01-01' },
  ]) assert.ok(policyValidation({ ...request, ...patch }), JSON.stringify(patch))
})

await test('회차 입력은 명시적인 양의 Java int: 자동 증가/임의 날짜 계산 없음', () => {
  for (const number of ['1', '2', '2147483647']) assert.equal(occurrenceValidation(number), '')
  for (const number of ['', '0', '-1', '1.5', '1e2', 'NaN', '2147483648']) assert.ok(occurrenceValidation(number))
})

await test('Backend 정책/권한 오류 안전한 메시지, 실패 후 동일 회차 재시도', async () => {
  for (const code of ['RETRAINING_POLICY_NOT_FOUND', 'INVALID_RETRAINING_SCHEDULE', 'INVALID_RETRAINING_OCCURRENCE', 'RETRAINING_SCHEDULE_LOCKED', 'RETRAINING_DISABLED', 'RETRAINING_OVERDUE', 'RETRAINING_DRAFT_PENDING', 'RETRAINING_NOT_OVERDUE', 'RETRAINING_SOURCE_INVALID', 'RETRAINING_ACTIVE_RULE_REQUIRED', 'ACCESS_DENIED']) {
    const error = new AxiosError('private trace', undefined, undefined, undefined, { status: code === 'ACCESS_DENIED' ? 403 : 409, data: { code, message: 'private trace' } })
    assert.ok(adminErrorMessage(error))
    assert.doesNotMatch(adminErrorMessage(error), /private trace/)
  }
  let fail = true
  await withAdapter(async config => {
    if (fail) throw new AxiosError('private trace', undefined, config, undefined, response(config, { code: 'RETRAINING_OVERDUE' }, 409))
    return response(config, course)
  }, async () => {
    await assert.rejects(retrainingApi.generate(4, 1), error => /건너뛰기/.test(adminErrorMessage(error)))
    fail = false
    assert.deepEqual(await retrainingApi.generate(4, 1), course)
  })
})

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
try {
  const ui = await server.ssrLoadModule('/src/pages/admin/RetrainingUI.tsx')
  const { PolicyFields, RetrainingPolicyForm } = await server.ssrLoadModule('/src/pages/admin/RetrainingPolicyForm.tsx')
  const { OccurrenceActions, RetrainingPolicyDetailPage } = await server.ssrLoadModule('/src/pages/admin/RetrainingPolicyDetailPage.tsx')
  const { RetrainingPoliciesPage } = await server.ssrLoadModule('/src/pages/admin/RetrainingPoliciesPage.tsx')
  const { ConfirmDialog } = await server.ssrLoadModule('/src/components/admin/Modal.tsx')
  const { AuthContext } = await server.ssrLoadModule('/src/auth/AuthContext.ts')
  const { AppSidebar } = await server.ssrLoadModule('/src/components/layout/AppSidebar.tsx')
  const { EmployeeSidebar } = await server.ssrLoadModule('/src/components/layout/EmployeeSidebar.tsx')
  const { RoleRoute } = await server.ssrLoadModule('/src/routes/RoleRoute.tsx')
  const { default: App } = await server.ssrLoadModule('/src/App.tsx')
  const render = (element, path = '/admin/retraining-policies') => renderToStaticMarkup(h(MemoryRouter, { initialEntries: [path] }, element))

  await test('정책 목록/상세에 실제 응답 필드, 상태, 자동화 및 과정 링크 표시', () => {
    const html = render(h(ui.PolicyTable, { policies: [policy, { ...policy, id: 5, enabled: false, autoCreate: false, autoOpen: true }] }))
    for (const value of ['보안 교육', '12개월', '2027-01-31', '30일', '7일', '1회차', '2027-01-24', '자동 생성 사용', '자동 OPEN 사용', '활성', '중지']) assert.ok(html.includes(value), value)
    assert.match(html, /href="\/admin\/courses\/7"/)
    assert.match(html, /href="\/admin\/retraining-policies\/4"/)
    assert.match(render(h(ui.PolicySummary, { policy })), /Asia\/Seoul/)
  })
  await test('긴 제목은 React 이스케이프 및 줄바꿈 가능한 표에 표시', () => {
    const html = render(h(ui.OccurrenceTable, { courses: [{ ...course, title: '가'.repeat(200) + '<script>bad</script>' }] }))
    assert.match(html, /&lt;script&gt;bad&lt;\/script&gt;/)
    assert.doesNotMatch(html, /<script>/)
    assert.match(html, /class="admin-table"/)
  })
  await test('회차 목록에 번호/날짜/상태 및 기존 과정 상세 이동', () => {
    const html = render(h(ui.OccurrenceTable, { courses: [course, { ...course, id: 81, status: 'OPEN', occurrenceNumber: 2 }, { ...course, id: 82, status: 'CLOSED', occurrenceNumber: 3 }] }))
    for (const value of ['1회차', '2회차', '3회차', '초안', '운영 중', '종료', '2027-01-31', '2027-03-01']) assert.ok(html.includes(value), value)
    assert.match(html, /href="\/admin\/courses\/80"/)
  })
  await test('생성 폼 시작일/범위/전체 설정과 autoOpen 즉시 공개 안내', () => {
    const html = render(h(PolicyFields, { value: request, locked: false, onChange: noop }))
    assert.match(html, /기준 과정 검색/)
    assert.match(html, /min="1" max="1200"/)
    assert.match(html, /min="0" max="3660"/)
    assert.match(html, /정책 활성화/)
    assert.match(html, /생성 직후 OPEN/)
    assert.match(html, /시작일에 자동 OPEN하는 기능이 아닙니다/)
    assert.doesNotMatch(html, /<fieldset[^>]*disabled/)
  })
  await test('잠금 후 기준 과정/핵심 일정 disabled, 제목과 스위치는 수정 가능', () => {
    const html = render(h(PolicyFields, { value: request, locked: true, onChange: noop }))
    assert.doesNotMatch(html, /기준 과정 검색/)
    assert.match(html, /aria-label="기준 교육과정" disabled=""/)
    assert.match(html, /<fieldset[^>]*disabled=""/)
    assert.match(html, /skip 이후/)
    assert.doesNotMatch(html.match(/<input[^>]*maxLength="150"[^>]*>/)?.[0] ?? '', /disabled/)
    assert.equal((html.match(/type="checkbox"/g) ?? []).length, 3)
  })
  await test('생성/수정 Modal과 저장 버튼, 생성 후 일정 잠금 유지', () => {
    const props = { onClose: noop, onSaved: noop, onReload: noop }
    assert.match(render(h(RetrainingPolicyForm, props)), /재교육 정책 생성/)
    const html = render(h(RetrainingPolicyForm, { ...props, policy: { ...policy, nextOccurrenceNumber: 2 } }))
    assert.match(html, /재교육 정책 수정/)
    assert.match(html, /정책 저장/)
    assert.match(html, /<fieldset[^>]*disabled/)
  })
  await test('회차 입력은 빈 값으로 시작, 서버 다음 회차 안내, 처리 중 입력/Action 비활성화', () => {
    const props = { pending: false, nextOccurrenceNumber: 9, onRequest: noop }
    const html = render(h(OccurrenceActions, props))
    assert.match(html, /다음 회차는 9회차/)
    assert.match(html, /value=""/)
    assert.match(html, /지정 회차 생성 \/ 조회/)
    assert.match(html, /지난 회차 건너뛰기/)
    assert.doesNotMatch(html, /disabled=/)
    assert.match(render(h(OccurrenceActions, { ...props, pending: true })), /<fieldset disabled=""/)
  })
  await test('skip Confirm에 명시적 회차/취소/확인, 대기 중 중복 확인 방지 및 오류 표시', () => {
    const props = { title: '지난 회차 건너뛰기 확인', description: '3회차를 건너뛸까요?', label: '3회차 건너뛰기', onConfirm: noop, onClose: noop, pending: false, error: '' }
    assert.match(render(h(ConfirmDialog, props)), /3회차를 건너뛸까요/)
    const html = render(h(ConfirmDialog, { ...props, pending: true, error: '아직 지난 회차가 아닙니다.' }))
    assert.match(html, /aria-busy="true"/)
    assert.equal((html.match(/disabled=""/g) ?? []).length, 3)
    assert.match(html, /role="alert"/)
    assert.match(html, /취소/)
  })
  await test('Loading / 정책·회차 Empty / 오류 재시도와 페이지 경계', () => {
    const props = { loading: false, error: '', empty: false, emptyMessage: '등록된 재교육 정책이 없습니다.', retry: noop, children: h('p', null, '목록') }
    assert.match(render(h(ui.RetrainingState, { ...props, loading: true })), /불러오고/)
    assert.match(render(h(ui.RetrainingState, { ...props, empty: true })), /등록된 재교육 정책이 없습니다/)
    assert.match(render(h(ui.RetrainingState, { ...props, empty: true, emptyMessage: '생성된 회차가 없습니다.' })), /생성된 회차가 없습니다/)
    const error = render(h(ui.RetrainingState, { ...props, error: '조회 실패' }))
    assert.match(error, /role="alert"/); assert.match(error, /다시 시도/); assert.doesNotMatch(error, />목록</)
    const pagination = render(h(ui.RetrainingPagination, { data: page([]), onPage: noop }))
    assert.match(pagination, /0 \/ 0/); assert.equal((pagination.match(/disabled=""/g) ?? []).length, 2)
    assert.match(render(h(RetrainingPoliciesPage)), /불러오고/)
  })
  await test('잘못된 정책 ID는 상세 API 화면을 마운트하지 않는다', () => {
    const html = render(h(Routes, null, h(Route, { path: '/admin/retraining-policies/:policyId', element: h(RetrainingPolicyDetailPage) })), '/admin/retraining-policies/invalid')
    assert.match(html, /올바른 정책 ID가 아닙니다/)
  })
  await test('실제 App의 재교육 Route 두 개는 ADMIN RoleRoute 안에만 등록', () => {
    const paths = []
    function visit(element, roles = []) {
      if (!element?.props) return
      const nextRoles = element.props.element?.type === RoleRoute ? [...roles, element.props.element.props.role] : roles
      if (element.props.path?.startsWith('/admin/retraining-policies')) paths.push([element.props.path, nextRoles])
      Children.forEach(element.props.children, child => visit(child, nextRoles))
    }
    visit(App())
    assert.deepEqual(paths, [['/admin/retraining-policies', ['ADMIN']], ['/admin/retraining-policies/:policyId', ['ADMIN']]])
  })
  await test('ADMIN만 재교육 메뉴/내용 노출; INSTRUCTOR/EMPLOYEE 직접 경로도 차단', () => {
    for (const role of ['ADMIN', 'INSTRUCTOR', 'EMPLOYEE']) {
      const auth = { user: { memberId: 1, email: 'test@local', roles: [role] }, isAuthenticated: true, isInitializing: false, login: noop, logout: noop }
      const wrap = child => h(AuthContext.Provider, { value: auth }, child)
      const path = role === 'INSTRUCTOR' ? '/instructor/courses' : role === 'EMPLOYEE' ? '/employee/learning' : '/admin/retraining-policies/4'
      const menu = render(wrap(h(role === 'EMPLOYEE' ? EmployeeSidebar : AppSidebar)), path)
      assert.equal(menu.includes('재교육 관리'), role === 'ADMIN')
      const route = h(Routes, null, h(Route, { element: h(RoleRoute, { role: 'ADMIN' }) }, h(Route, { path: '*', element: h('p', null, 'RETRAINING_PRIVATE') })))
      assert.equal(render(wrap(route)).includes('RETRAINING_PRIVATE'), role === 'ADMIN')
    }
  })
} finally { await server.close() }
