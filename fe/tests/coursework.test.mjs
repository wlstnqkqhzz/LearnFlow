import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'
import { AxiosError } from 'axios'
import { apiClient } from '../src/api/client.ts'
import { courseworkApi } from '../src/api/courseworkApi.ts'
import { adminErrorMessage } from '../src/api/adminError.ts'
import { canManageAssignments, canSubmitAssignment, submissionValidation, submissionTime, scoreText } from '../src/pages/coursework/courseworkUtils.ts'
import { moveContent } from '../src/pages/admin/courseUtils.ts'

const assignment = { assignmentId: 3, courseId: 2, title: '실무 과제', description: '긴 설명 <script>alert(1)</script>', required: true, dueDate: '2026-10-31', passingScore: 70, sortOrder: 1 }
const submission = { submissionId: 7, assignmentId: 3, enrollmentId: 9, memberId: 4, memberName: '직원 가', submissionType: 'TEXT', content: '작성한 답안', submittedAt: '2026-09-28T01:00:00', submissionCount: 2, status: 'PENDING_GRADING', score: null, passed: null, feedback: null, gradedAt: null, gradedByMemberId: null, gradedByName: null, version: 0 }
const item = { assignment, status: 'NOT_SUBMITTED', effectiveDueDate: '2026-10-30', submittable: true, resubmittable: false, submission: null }
const course = { id: 2, title: '교육 과정', status: 'DRAFT', instructorId: 5 }
const noop = () => {}

await test('coursework 12개 API의 실제 URI/메서드/DTO/검색 조건', async () => {
  const previous = apiClient.defaults.adapter, calls = []
  const signal = new AbortController().signal
  apiClient.defaults.adapter = async config => { calls.push(config); return { config, data: item, status: 200, statusText: '', headers: {} } }
  try {
    const request = { title: '과제', description: null, required: true, dueDate: '2026-10-31', passingScore: 70, sortOrder: 1 }
    await courseworkApi.list(2, signal)
    await courseworkApi.get(2, 3, signal)
    await courseworkApi.create(2, request)
    await courseworkApi.update(2, 3, request)
    await courseworkApi.remove(2, 3)
    await courseworkApi.reorder(2, [8, 3])
    const filters = { page: 0, size: 20, assignmentId: 3, memberId: 4, enrollmentId: 9, status: 'FAILED' }
    await courseworkApi.submissions(2, filters, signal)
    await courseworkApi.submission(7, signal)
    await courseworkApi.grade(7, { score: 40, feedback: '보완 필요', passed: true })
    await courseworkApi.mine(9, signal)
    await courseworkApi.mineOne(9, 3, signal)
    assert.deepEqual(await courseworkApi.submit(9, 3, { submissionType: 'URL', content: 'https://example.com', memberId: 99 }), item)
    assert.deepEqual(calls.map(c => [c.method, c.url]), [
      ['get', '/courses/2/assignments'], ['get', '/courses/2/assignments/3'], ['post', '/courses/2/assignments'],
      ['patch', '/courses/2/assignments/3'], ['delete', '/courses/2/assignments/3'], ['patch', '/courses/2/assignments/order'],
      ['get', '/courses/2/assignment-submissions'], ['get', '/assignment-submissions/7'], ['patch', '/assignment-submissions/7/grade'],
      ['get', '/enrollments/9/assignments'], ['get', '/enrollments/9/assignments/3'], ['put', '/enrollments/9/assignments/3/submission'],
    ])
    assert.deepEqual(JSON.parse(calls[2].data), request)
    assert.deepEqual(JSON.parse(calls[3].data), request)
    assert.deepEqual(JSON.parse(calls[5].data), { assignmentIds: [8, 3] })
    assert.deepEqual(calls[6].params, filters)
    assert.equal(calls[0].signal, signal)
    assert.deepEqual(JSON.parse(calls[8].data), { score: 40, feedback: '보완 필요' })
    assert.deepEqual(JSON.parse(calls[11].data), { submissionType: 'URL', content: 'https://example.com' })
    await courseworkApi.submit(9, 3, { submissionType: 'TEXT', content: '재제출' })
    assert.deepEqual(JSON.parse(calls[12].data), { submissionType: 'TEXT', content: '재제출' })
  } finally { apiClient.defaults.adapter = previous }
})
await test('ADMIN/담당 INSTRUCTOR만 관리, 직원/다른 강사 차단', () => {
  assert.equal(canManageAssignments({ memberId: 1, roles: ['ADMIN'] }, course), true)
  assert.equal(canManageAssignments({ memberId: 5, roles: ['INSTRUCTOR'] }, course), true)
  for (const user of [null, { memberId: 6, roles: ['INSTRUCTOR'] }, { memberId: 5, roles: ['EMPLOYEE'] }]) assert.equal(canManageAssignments(user, course), false)
})
await test('서버 제출 가능 값 우선, 채점 대기/FAIL 재제출 및 PASS 차단', () => {
  assert.equal(canSubmitAssignment(item), true)
  assert.equal(canSubmitAssignment({ ...item, effectiveDueDate: '2000-01-01' }), true)
  assert.equal(canSubmitAssignment({ ...item, submittable: false }), false)
  for (const status of ['PENDING_GRADING', 'FAILED']) {
    assert.equal(canSubmitAssignment({ ...item, status, submission, resubmittable: true }), true)
    assert.equal(canSubmitAssignment({ ...item, status, submission, resubmittable: false }), false)
    assert.equal(canSubmitAssignment({ ...item, status, submission, resubmittable: true, submittable: false }), false)
  }
  assert.equal(canSubmitAssignment({ ...item, status: 'PASSED', submission, resubmittable: true }), false)
})
await test('TEXT/URL 검증, nullable 점수, 서울 시각, 순서 변경', () => {
  assert.equal(submissionValidation({ submissionType: 'TEXT', content: '내용' }), '')
  assert.ok(submissionValidation({ submissionType: 'TEXT', content: ' ' }))
  assert.ok(submissionValidation({ submissionType: 'TEXT', content: 'a'.repeat(10001) }))
  assert.equal(submissionValidation({ submissionType: 'URL', content: 'https://example.com/doc' }), '')
  for (const content of ['javascript:alert(1)', '/relative', 'https://example.com/' + 'a'.repeat(2048)]) assert.ok(submissionValidation({ submissionType: 'URL', content }))
  assert.equal(scoreText(null), '미채점'); assert.equal(scoreText(0), '0.00점')
  assert.match(submissionTime('2026-09-28T01:00:00'), /10:00/)
  assert.deepEqual(moveContent([3, 8], 1, -1), [8, 3])
})
await test('권한/마감/확정 채점 오류는 사용자 메시지로 처리', () => {
  for (const [status, code] of [[403, 'ACCESS_DENIED'], [409, 'ASSIGNMENT_ALREADY_GRADED'], [400, 'ASSIGNMENT_DEADLINE_PASSED']]) {
    const error = new AxiosError('private trace', undefined, undefined, undefined, { status, data: { code, message: 'private trace' }, headers: {}, config: {} })
    assert.ok(adminErrorMessage(error)); assert.doesNotMatch(adminErrorMessage(error), /private trace/)
  }
})

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
try {
  const { AssignmentList, CourseAssignmentsSection } = await server.ssrLoadModule('/src/pages/coursework/CourseAssignmentsSection.tsx')
  const { EmployeeAssignmentCard } = await server.ssrLoadModule('/src/pages/coursework/EmployeeAssignmentsSection.tsx')
  const { SubmissionTable, GradeForm } = await server.ssrLoadModule('/src/pages/coursework/SubmissionsSection.tsx')
  const { CourseworkState, SubmissionInfo } = await server.ssrLoadModule('/src/pages/coursework/CourseworkUI.tsx')
  const { AuthContext } = await server.ssrLoadModule('/src/auth/AuthContext.ts')
  const render = element => renderToStaticMarkup(element)
  await test('DRAFT 구성 Action, OPEN/CLOSED 조회 전용, 미담당 강사 차단', () => {
    for (const status of ['DRAFT', 'OPEN', 'CLOSED']) {
      const html = render(h(AuthContext.Provider, { value: { user: { memberId: 5, roles: ['INSTRUCTOR'] } } }, h(CourseAssignmentsSection, { course: { ...course, status } })))
      assert.equal(html.includes('+ 과제 추가'), status === 'DRAFT')
      const list = render(h(AssignmentList, { assignments: [assignment], editable: status === 'DRAFT', busy: false, dirty: false, onEdit: noop, onDelete: noop, onMove: noop }))
      assert.equal(list.includes('>수정</button>'), status === 'DRAFT')
      assert.equal(list.includes('>삭제</button>'), status === 'DRAFT')
      assert.equal(list.includes('위로 이동'), status === 'DRAFT')
      assert.match(list, /필수/); assert.match(list, /2026-10-31/); assert.match(list, /70점/)
    }
    const denied = render(h(AuthContext.Provider, { value: { user: { memberId: 6, roles: ['INSTRUCTOR'] } } }, h(CourseAssignmentsSection, { course })))
    assert.match(denied, /권한이 없습니다/); assert.doesNotMatch(denied, /과제 추가/)
  })
  await test('정렬된 목록 순서와 저장 중 Action disabled', () => {
    const html = render(h(AssignmentList, { assignments: [{ ...assignment, assignmentId: 8, title: '첫 번째' }, assignment], editable: true, busy: true, dirty: true, onEdit: noop, onDelete: noop, onMove: noop }))
    assert.ok(html.indexOf('첫 번째') < html.indexOf('실무 과제'))
    assert.match(html, /disabled=""/)
  })
  await test('직원 TEXT/URL 입력과 미제출/대기/FAIL/PASS/마감 UX', () => {
    const view = value => render(h(EmployeeAssignmentCard, { item: value, enrollmentId: 9, onSaved: noop }))
    assert.match(view(item), /<textarea/); assert.match(view(item), /과제 제출/)
    for (const status of ['PENDING_GRADING', 'FAILED']) {
      const value = { ...item, status, resubmittable: true, submission: { ...submission, status, submissionType: 'URL', content: 'https://example.com/doc' } }
      assert.match(view(value), /type="url"/); assert.match(view(value), />재제출</)
      assert.doesNotMatch(view({ ...value, submittable: false }), /<form/)
    }
    const passed = view({ ...item, status: 'PASSED', resubmittable: true, submission: { ...submission, status: 'PASSED', score: 80, feedback: '잘했습니다' } })
    assert.doesNotMatch(passed, /<form/); assert.match(passed, /80.00점/); assert.match(passed, /잘했습니다/)
    assert.match(passed, /재제출할 수 없습니다/)
    assert.doesNotMatch(view({ ...item, submittable: false }), /<form/)
    assert.doesNotMatch(view(item), /<script>/)
  })
  await test('제출물 직원/과제/횟수/서버 결과 표시와 재채점 Action 차단', () => {
    const rows = [submission, { ...submission, submissionId: 8, status: 'PASSED', score: 85, gradedAt: '2026-09-28T02:00:00' }, { ...submission, submissionId: 9, status: 'FAILED', score: 30, gradedAt: '2026-09-28T02:00:00' }]
    const html = render(h(SubmissionTable, { rows, assignments: [assignment], onSelect: noop }))
    for (const value of ['직원 가', '실무 과제', '2회', '채점 대기', '불합격', '85.00점', '미채점']) assert.ok(html.includes(value))
    assert.equal((html.match(/조회 \/ 채점/g) ?? []).length, 1)
    assert.equal((html.match(/결과 보기/g) ?? []).length, 2)
    const form = render(h(GradeForm, { pending: true, onSubmit: async () => {} }))
    assert.match(form, /fieldset disabled=""/); assert.match(form, /name="score"/); assert.match(form, /name="feedback"/); assert.doesNotMatch(form, /name="passed"/)
  })
  await test('Loading/Empty/Error와 nullable 피드백, 긴 URL 및 XSS 안전 표시', () => {
    const state = { loading: false, error: '', empty: false, emptyMessage: '등록된 과제가 없습니다.', retry: noop }
    assert.match(render(h(CourseworkState, { ...state, loading: true })), /role="status"/)
    assert.match(render(h(CourseworkState, { ...state, empty: true })), /등록된 과제가 없습니다/)
    assert.match(render(h(CourseworkState, { ...state, error: '조회 실패' })), /조회 실패/)
    const html = render(h(SubmissionInfo, { submission }))
    assert.match(html, /미채점/); assert.match(html, /등록된 피드백이 없습니다/)
    const url = render(h(SubmissionInfo, { submission: { ...submission, submissionType: 'URL', content: 'https://example.com/' + 'a'.repeat(1000) } }))
    assert.match(url, /coursework-copy/); assert.match(url, /rel="noopener noreferrer"/)
    assert.doesNotMatch(render(h(SubmissionInfo, { submission: { ...submission, submissionType: 'URL', content: 'javascript:alert(1)', feedback: '<script>bad</script>' } })), /href=|<script>/)
  })
} finally { await server.close() }
