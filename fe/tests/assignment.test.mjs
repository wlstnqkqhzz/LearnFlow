import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'
import { apiClient } from '../src/api/client.ts'
import { assignmentRuleApi } from '../src/api/assignmentRuleApi.ts'
import { enrollmentApi } from '../src/api/enrollmentApi.ts'
import { assignmentSources, eligibleForManualAssignment, enrollmentStatuses, ruleRequest, ruleTarget, ruleTypes } from '../src/pages/admin/assignmentUtils.ts'

function capture(data) { const calls = []; apiClient.defaults.adapter = async config => { calls.push(config); return { config, data, status: 200, statusText: '', headers: {} } }; return calls }
function body(call) { return call.data ? JSON.parse(call.data) : undefined }
const rule = { id: 3, courseId: 7, ruleType: 'DEPARTMENT', departmentId: 2, jobPositionId: null, newEmployeeDays: null, active: true }
const enrollment = { enrollmentId: 9, memberId: 4, memberName: '김직원', courseId: 7, courseTitle: '보안교육', status: 'EXPIRED', assignmentSource: 'AUTOMATIC', assignmentRuleId: 3, assignedAt: '2026-09-01T00:00:00', startedAt: null, completedAt: null, dueDate: '2026-09-30' }

await test('규칙 API는 과정 소속 경로에서 생성·수정·상태 변경', async () => { const calls = capture(rule); const request = ruleRequest('DEPARTMENT', 2); await assignmentRuleApi.list(7); await assignmentRuleApi.create(7, { ...request, active: true }); await assignmentRuleApi.update(7, 3, ruleRequest('NEW_EMPLOYEE', 90)); await assignmentRuleApi.status(7, 3, false); assert.deepEqual(calls.map(c => [c.method, c.url]), [['get', '/courses/7/assignment-rules'], ['post', '/courses/7/assignment-rules'], ['patch', '/courses/7/assignment-rules/3'], ['patch', '/courses/7/assignment-rules/3/status']]) })
await test('규칙 유형 변경 시 이전 대상 필드는 null로 제거', () => { assert.deepEqual(ruleRequest('ALL_EMPLOYEES', 2), { ruleType: 'ALL_EMPLOYEES', departmentId: null, jobPositionId: null, newEmployeeDays: null }); assert.deepEqual(ruleRequest('JOB_POSITION', 8), { ruleType: 'JOB_POSITION', departmentId: null, jobPositionId: 8, newEmployeeDays: null }); assert.deepEqual(ruleRequest('NEW_EMPLOYEE', 90), { ruleType: 'NEW_EMPLOYEE', departmentId: null, jobPositionId: null, newEmployeeDays: 90 }) })
await test('규칙 유형과 대상은 한국어로 표시', () => { assert.equal(ruleTypes.ALL_EMPLOYEES, '전체 직원'); assert.equal(ruleTypes.NEW_EMPLOYEE, '신입사원'); assert.equal(ruleTarget(rule, [{ id: 2, name: '백엔드팀' }], []), '백엔드팀') })
await test('수강 조회는 Backend 지원 status/page/size만 과정별 전달', async () => { const page = { content: [enrollment], page: 0, size: 20, totalElements: 1, totalPages: 1 }; const calls = capture(page); const params = { page: 0, size: 20, status: 'EXPIRED' }; assert.deepEqual(await enrollmentApi.forCourse(7, params), page); assert.deepEqual(calls[0].params, params) })
await test('수동 배정은 memberId만 전송', async () => { const calls = capture(enrollment); await enrollmentApi.assign(7, 4); assert.equal(calls[0].url, '/courses/7/enrollments'); assert.deepEqual(body(calls[0]), { memberId: 4 }) })
await test('재직·휴직은 수동 배정 가능하고 퇴사자는 제외', () => { assert.equal(eligibleForManualAssignment('ACTIVE'), true); assert.equal(eligibleForManualAssignment('ON_LEAVE'), true); assert.equal(eligibleForManualAssignment('RESIGNED'), false) })
await test('수강 상태와 배정 출처 한국어 매핑', () => { assert.deepEqual(Object.values(enrollmentStatuses).map(v => v.label), ['배정됨', '학습 중', '수료', '실패', '만료']); assert.deepEqual(assignmentSources, { MANUAL: '수동 배정', AUTOMATIC: '자동 배정' }) })

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
try {
  const { EnrollmentTable, EnrollmentStatusBadge } = await server.ssrLoadModule('/src/pages/admin/EnrollmentsPage.tsx')
  const { AssignmentRuleModal } = await server.ssrLoadModule('/src/pages/admin/AssignmentRuleModal.tsx')
  await test('수강 목록은 상태·출처·규칙을 표시하고 변경/삭제 Action이 없다', () => { const html = renderToStaticMarkup(h(EnrollmentTable, { enrollments: [enrollment], onDetail: () => {} })); assert.match(html, /김직원/); assert.match(html, /자동 배정/); assert.match(html, /규칙 #3/); assert.doesNotMatch(html, /상태 변경|삭제/) })
  await test('완료·실패·만료 Badge를 텍스트와 함께 표시', () => { const html = ['COMPLETED', 'FAILED', 'EXPIRED'].map(status => renderToStaticMarkup(h(EnrollmentStatusBadge, { status }))).join(''); assert.ok(['수료', '실패', '만료'].every(label => html.includes(label))) })
  await test('규칙 유형별 Form은 해당 대상 입력만 렌더링', () => { const base = { courseId: 7, courseStatus: 'DRAFT', departments: [{ id: 2, name: '백엔드팀', isActive: true }], jobs: [{ id: 8, name: '개발자', isActive: true }], onClose: () => {}, onSaved: () => {} }; const forms = [['ALL_EMPLOYEES', '전체 직원이 자동 배정 대상'], ['DEPARTMENT', '대상 부서'], ['JOB_POSITION', '대상 직무'], ['NEW_EMPLOYEE', '입사 후 일수']].map(([ruleType, label], index) => { const value = { id: index + 1, courseId: 7, ruleType, departmentId: ruleType === 'DEPARTMENT' ? 2 : null, jobPositionId: ruleType === 'JOB_POSITION' ? 8 : null, newEmployeeDays: ruleType === 'NEW_EMPLOYEE' ? 90 : null, active: true }; return [renderToStaticMarkup(h(AssignmentRuleModal, { ...base, rule: value })), label] }); for (const [html, label] of forms) assert.ok(html.includes(label)) })
} finally { await server.close() }
