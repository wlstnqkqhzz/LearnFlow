import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { createServer } from 'vite'
import { apiClient } from '../src/api/client.ts'
import { employeeLearningApi } from '../src/api/employeeLearningApi.ts'
import { dueLabel, learningContentTypes, learningCourseTypes, learningStatuses, safeResourceUrl } from '../src/pages/employee/learningUtils.ts'

function capture(data) { const calls = []; apiClient.defaults.adapter = async config => { calls.push(config); return { config, data, status: 200, statusText: '', headers: {} } }; return calls }
function body(call) { return call.data ? JSON.parse(call.data) : undefined }

const enrollment = { enrollmentId: 10, memberId: 2, memberName: '김직원', courseId: 1, courseTitle: '정보보안 교육', courseType: 'MANDATORY', courseStartDate: '2026-09-01', courseEndDate: '2026-09-30', status: 'ASSIGNED', assignmentSource: 'MANUAL', assignmentRuleId: null, assignedAt: '2026-09-01T00:00:00', startedAt: null, completedAt: null, dueDate: '2026-09-30' }
const detail = { enrollmentId: 10, status: 'IN_PROGRESS', dueDate: '2026-09-30', assignedAt: '2026-09-01T00:00:00', startedAt: '2026-09-02T00:00:00', completedAt: null, courseId: 1, courseTitle: '정보보안 교육', courseDescription: '필수 보안 교육', courseType: 'MANDATORY', courseStartDate: '2026-09-01', courseEndDate: '2026-09-30', instructorId: null, instructorName: null, progressRate: 50, passingProgressRate: 80, contentConditionSatisfied: false, contents: [
  { contentId: 1, title: '동영상', contentType: 'VIDEO', contentUrl: 'https://example.invalid/video', durationSeconds: 600, required: true, sortOrder: 2, progressRate: 100, completedAt: '2026-09-02T01:00:00' },
  { contentId: 2, title: '문서', contentType: 'DOCUMENT', contentUrl: 'https://example.invalid/document', durationSeconds: null, required: true, sortOrder: 1, progressRate: 0, completedAt: null },
  { contentId: 3, title: '참고 링크', contentType: 'LINK', contentUrl: 'https://example.invalid/link', durationSeconds: null, required: false, sortOrder: 3, progressRate: 0, completedAt: null },
] }

await test('본인 수강 목록은 status/page/size만 전달하고 관리자 API를 사용하지 않음', async () => {
  const page = { content: [enrollment], page: 0, size: 12, totalElements: 1, totalPages: 1 }
  const calls = capture(page)
  assert.deepEqual(await employeeLearningApi.list({ page: 0, size: 12, status: 'ASSIGNED' }), page)
  assert.equal(calls[0].url, '/enrollments/me')
  assert.deepEqual(calls[0].params, { page: 0, size: 12, status: 'ASSIGNED' })
})
await test('교육 상세는 소유권 검증된 progress API만 사용', async () => {
  const calls = capture(detail)
  assert.deepEqual(await employeeLearningApi.detail(10), detail)
  assert.equal(calls[0].url, '/enrollments/10/progress')
})
await test('학습 완료는 100% 진도만 전송하고 갱신된 상세 응답을 사용', async () => {
  const calls = capture({ ...detail, status: 'COMPLETED', progressRate: 100 })
  const result = await employeeLearningApi.completeContent(10, 2)
  assert.equal(calls[0].method, 'patch')
  assert.equal(calls[0].url, '/enrollments/10/contents/2/progress')
  assert.deepEqual(body(calls[0]), { progressRate: 100 })
  assert.equal(result.status, 'COMPLETED')
})
await test('상태·과정·콘텐츠 유형은 Backend Enum과 한국어 Label을 분리', () => {
  assert.deepEqual(Object.values(learningStatuses).map(value => value.label), ['학습 전', '학습 중', '수료', '실패', '만료'])
  assert.deepEqual(learningCourseTypes, { MANDATORY: '필수교육', OPTIONAL: '선택교육' })
  assert.deepEqual(learningContentTypes, { VIDEO: '동영상', DOCUMENT: '문서', LINK: '링크' })
  assert.equal(dueLabel('2026-09-24', new Date('2026-09-22T01:00:00Z')), '마감 D-2')
  assert.equal(safeResourceUrl('javascript:alert(1)'), null)
  assert.equal(safeResourceUrl('https://example.invalid/resource'), 'https://example.invalid/resource')
})

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
try {
  const { EnrollmentCard, LearningStatusBadge } = await server.ssrLoadModule('/src/pages/employee/LearningPage.tsx')
  const { LearningContentList, LearningSummary } = await server.ssrLoadModule('/src/pages/employee/LearningDetailPage.tsx')
  const { ContentLearningView } = await server.ssrLoadModule('/src/pages/employee/ContentLearningPage.tsx')
  const { RoleRoute } = await server.ssrLoadModule('/src/routes/RoleRoute.tsx')
  const { AuthContext } = await server.ssrLoadModule('/src/auth/AuthContext.ts')

  await test('내 교육 Card는 과정 유형·상태·기간·마감일을 표시', () => {
    const html = renderToStaticMarkup(h(MemoryRouter, null, h(EnrollmentCard, { enrollment })))
    assert.ok(['정보보안 교육', '필수교육', '학습 전', '2026-09-01 ~ 2026-09-30', '마감'].every(value => html.includes(value)))
  })
  await test('수강 상태 5종을 한국어 Badge로 표시', () => {
    const html = ['ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'EXPIRED'].map(status => renderToStaticMarkup(h(LearningStatusBadge, { status }))).join('')
    assert.ok(['학습 전', '학습 중', '수료', '실패', '만료'].every(label => html.includes(label)))
  })
  await test('교육 상세는 진도 기준·강사 미지정·필수/선택·누락 진도를 표시', () => {
    const summary = renderToStaticMarkup(h(LearningSummary, { detail }))
    const contents = renderToStaticMarkup(h(MemoryRouter, null, h(LearningContentList, { detail })))
    assert.match(summary, /강사 미지정/)
    assert.ok(['문서', '동영상', '참고 링크', '필수', '선택', '학습 전'].every(value => contents.includes(value)))
    assert.ok(contents.indexOf('문서') < contents.indexOf('동영상'))
  })
  await test('VIDEO·DOCUMENT·LINK는 안전한 새 탭 링크로 열린다', () => {
    for (const content of detail.contents) {
      const html = renderToStaticMarkup(h(ContentLearningView, { detail, contentId: content.contentId, pending: false, feedback: '', error: '', onComplete: () => {} }))
      assert.match(html, /target="_blank"/)
      assert.match(html, /rel="noopener noreferrer"/)
      assert.ok(html.includes(learningContentTypes[content.contentType]))
    }
  })
  await test('완료·실패·만료에서는 진도 수정 Action을 노출하지 않음', () => {
    for (const status of ['COMPLETED', 'FAILED', 'EXPIRED']) {
      const html = renderToStaticMarkup(h(ContentLearningView, { detail: { ...detail, status, contents: [{ ...detail.contents[1], progressRate: 0 }] }, contentId: 2, pending: false, feedback: '', error: '', onComplete: () => {} }))
      assert.doesNotMatch(html, />학습 완료<\/button>/)
      assert.match(html, /변경할 수 없습니다|읽기 전용/)
    }
  })
  await test('저장 중에는 학습 완료 버튼을 비활성화', () => {
    const html = renderToStaticMarkup(h(ContentLearningView, { detail, contentId: 2, pending: true, feedback: '', error: '', onComplete: () => {} }))
    assert.match(html, /disabled=""/)
    assert.match(html, /저장 중/)
  })
  await test('RoleRoute는 EMPLOYEE 포함 계정만 학습 Route를 허용', () => {
    for (const roles of [['EMPLOYEE'], ['EMPLOYEE', 'ADMIN'], ['ADMIN'], ['INSTRUCTOR']]) {
      const value = { user: { memberId: 2, email: 'user@example.com', roles }, isAuthenticated: true, isInitializing: false, login: async () => {}, logout: async () => {} }
      const html = renderToStaticMarkup(h(AuthContext.Provider, { value }, h(MemoryRouter, { initialEntries: ['/employee/learning'] }, h(Routes, null, h(Route, { element: h(RoleRoute, { role: 'EMPLOYEE' }) }, h(Route, { path: '/employee/learning', element: h('p', null, '내 교육') })), h(Route, { path: '/403', element: h('p', null, '권한 없음') })))))
      assert.equal(html.includes('내 교육'), roles.includes('EMPLOYEE'))
    }
  })
} finally { await server.close() }
