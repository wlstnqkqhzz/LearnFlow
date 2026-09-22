import assert from 'node:assert/strict'
import { test } from 'node:test'
import { AxiosError } from 'axios'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { createServer } from 'vite'
import { apiClient } from '../src/api/client.ts'
import { courseApi } from '../src/api/courseApi.ts'
import { courseContentApi } from '../src/api/courseContentApi.ts'
import { adminErrorMessage } from '../src/api/adminError.ts'
import { contentTypes, coursePeriod, courseStatuses, courseTypes, moveContent, nextCourseStatus, openValidation } from '../src/pages/admin/courseUtils.ts'

const course = {
  id: 7, title: '정보보안 필수교육 2026', description: null, courseType: 'MANDATORY', status: 'DRAFT',
  startDate: '2026-09-01', endDate: '2026-09-30', passingProgressRate: 100,
  instructorId: null, instructorName: null, createdAt: '2026-09-22T00:00:00', updatedAt: '2026-09-22T00:00:00',
}
const contents = [
  { id: 11, courseId: 7, title: '영상', contentType: 'VIDEO', contentUrl: 'https://example.invalid/video', durationSeconds: 600, sortOrder: 1, required: true },
  { id: 12, courseId: 7, title: '가이드', contentType: 'DOCUMENT', contentUrl: 'https://example.invalid/doc', durationSeconds: null, sortOrder: 2, required: true },
  { id: 13, courseId: 7, title: '규정', contentType: 'LINK', contentUrl: 'https://example.invalid/link', durationSeconds: null, sortOrder: 3, required: false },
]
function capture(data) {
  const calls = []
  apiClient.defaults.adapter = async config => { calls.push(config); return { config, data, status: 200, statusText: '', headers: {} } }
  return calls
}
function body(call) { return call.data ? JSON.parse(call.data) : undefined }

await test('과정 검색은 Backend 지원 필터와 페이지, 취소 signal만 전달', async () => {
  const page = { content: [course], page: 1, size: 10, totalElements: 11, totalPages: 2 }
  const calls = capture(page)
  const signal = new AbortController().signal
  const params = { page: 1, size: 10, keyword: '보안', status: 'DRAFT', type: 'MANDATORY', instructorId: 3 }
  assert.deepEqual(await courseApi.list(params, signal), page)
  assert.deepEqual(calls[0].params, params)
  assert.equal(calls[0].signal, signal)
})
await test('과정 생성은 상태 없이 선택 강사 null을 그대로 전송', async () => {
  const calls = capture(course)
  const request = { title: course.title, description: null, courseType: 'MANDATORY', startDate: null, endDate: null, passingProgressRate: 100, instructorId: null }
  assert.equal((await courseApi.create(request)).status, 'DRAFT')
  assert.equal(calls[0].method, 'post')
  assert.equal(calls[0].url, '/courses')
  assert.deepEqual(body(calls[0]), request)
  assert.equal(Object.hasOwn(body(calls[0]), 'status'), false)
})
await test('과정 일반 수정과 상태 변경은 서로 다른 PATCH Endpoint 사용', async () => {
  const calls = capture(course)
  await courseApi.update(7, { title: '수정 과정', instructorId: null })
  await courseApi.status(7, 'OPEN')
  await courseApi.status(7, 'CLOSED')
  assert.deepEqual(calls.map(call => [call.method, call.url]), [['patch', '/courses/7'], ['patch', '/courses/7/status'], ['patch', '/courses/7/status']])
  assert.deepEqual(body(calls[0]), { title: '수정 과정', instructorId: null })
  assert.deepEqual(calls.slice(1).map(body), [{ status: 'OPEN' }, { status: 'CLOSED' }])
})
await test('과정 상태 Action은 정방향 전이만 제공', () => {
  assert.equal(nextCourseStatus('DRAFT'), 'OPEN')
  assert.equal(nextCourseStatus('OPEN'), 'CLOSED')
  assert.equal(nextCourseStatus('CLOSED'), null)
})
await test('강사 미지정은 OPEN을 막지 않고 날짜 누락만 안내', () => {
  assert.equal(openValidation(course), '')
  assert.match(openValidation({ startDate: null, endDate: null }), /시작일과 종료일/)
  assert.match(openValidation({ startDate: '2026-10-01', endDate: '2026-09-30' }), /늦을 수/)
})
await test('과정/콘텐츠 Enum은 한국어 표시값과 분리', () => {
  assert.deepEqual(courseTypes, { MANDATORY: '필수교육', OPTIONAL: '선택교육' })
  assert.deepEqual(Object.values(courseStatuses).map(item => item.label), ['초안', '운영 중', '종료'])
  assert.deepEqual(contentTypes, { VIDEO: '동영상', DOCUMENT: '문서', LINK: '링크' })
  assert.equal(coursePeriod({ startDate: null, endDate: null }), '기간 미정')
})
await test('콘텐츠 목록/생성/수정/삭제는 courseId + contentId 소속 경로 사용', async () => {
  const calls = capture(contents)
  await courseContentApi.list(7)
  await courseContentApi.create(7, { title: '영상', contentType: 'VIDEO', contentUrl: 'https://example.invalid', durationSeconds: null, sortOrder: 4, required: true })
  await courseContentApi.update(7, 11, { title: '수정 영상', required: false })
  await courseContentApi.delete(7, 11)
  assert.deepEqual(calls.map(call => [call.method, call.url]), [['get', '/courses/7/contents'], ['post', '/courses/7/contents'], ['patch', '/courses/7/contents/11'], ['delete', '/courses/7/contents/11']])
  assert.deepEqual(body(calls[2]), { title: '수정 영상', required: false })
})
await test('전체 순서는 현재 콘텐츠 ID 전체를 요청 순서대로 전송', async () => {
  const calls = capture(contents)
  await courseContentApi.reorder(7, [13, 11, 12])
  assert.equal(calls[0].url, '/courses/7/contents/order')
  assert.deepEqual(body(calls[0]), { contentIds: [13, 11, 12] })
})
await test('위/아래 이동은 원본을 바꾸지 않고 경계를 넘지 않는다', () => {
  const ids = [11, 12, 13]
  assert.deepEqual(moveContent(ids, 1, -1), [12, 11, 13])
  assert.deepEqual(moveContent(ids, 1, 1), [11, 13, 12])
  assert.equal(moveContent(ids, 0, -1), ids)
  assert.deepEqual(ids, [11, 12, 13])
})
await test('Course 404, 상태/삭제 충돌은 내부 오류를 노출하지 않는다', () => {
  const error = (status, code) => new AxiosError('SQL stack', undefined, undefined, undefined, { status, data: { code, message: 'internal SQL' } })
  assert.match(adminErrorMessage(error(404, 'COURSE_NOT_FOUND')), /교육과정을 찾을/)
  assert.match(adminErrorMessage(error(409, 'INVALID_COURSE_STATUS_TRANSITION')), /현재 상태/)
  assert.match(adminErrorMessage(error(409, 'DATA_CONFLICT')), /사용 중/)
  assert.doesNotMatch(adminErrorMessage(error(500, 'INTERNAL_SERVER_ERROR')), /SQL|stack/)
})

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
try {
  const { CourseTable } = await server.ssrLoadModule('/src/pages/admin/CoursesPage.tsx')
  const badges = await server.ssrLoadModule('/src/components/admin/CourseBadges.tsx')
  const { CourseForm } = await server.ssrLoadModule('/src/pages/admin/CourseForm.tsx')
  await test('과정 목록은 유형/상태/강사 미지정을 Table로 렌더링', () => {
    const html = renderToStaticMarkup(h(MemoryRouter, null, h(CourseTable, { courses: [course] })))
    assert.match(html, /정보보안 필수교육 2026/)
    assert.match(html, /필수교육/)
    assert.match(html, /초안/)
    assert.match(html, /강사 미지정/)
    assert.match(html, /2026-09-01 ~ 2026-09-30/)
  })
  await test('콘텐츠 세 유형과 필수/선택 의미의 표시 기반을 렌더링', () => {
    const html = ['VIDEO', 'DOCUMENT', 'LINK'].map(type => renderToStaticMarkup(h(badges.ContentTypeBadge, { type }))).join('')
    assert.ok(['동영상', '문서', '링크'].every(label => html.includes(label)))
  })
  await test('과정 Form은 강사를 선택 입력으로 두고 날짜/진도 범위를 표시', () => {
    const html = renderToStaticMarkup(h(CourseForm, { pending: false, submitLabel: '만들기', onSubmit: async () => {} }))
    assert.match(html, /강사 회원 ID/)
    assert.doesNotMatch(html, /name="instructorId"[^>]*required/)
    const progressInput = html.match(/<input[^>]*name="passingProgressRate"[^>]*>|<input[^>]*passingProgressRate[^>]*>/)?.[0] ?? ''
    assert.match(progressInput, /min="0"/)
    assert.match(progressInput, /max="100"/)
  })
} finally { await server.close() }
