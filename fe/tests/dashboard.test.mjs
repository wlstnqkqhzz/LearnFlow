import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { createServer } from 'vite'
import { apiClient } from '../src/api/client.ts'
import { dashboardApi } from '../src/api/dashboardApi.ts'

// Runtime Mock이 아닌 테스트 전용 응답 fixture.
const data = {
  overview: { employeeCount: 17, openCourseCount: 8, ongoingEnrollmentCount: 5, completionRate: 33.33 },
  distribution: { total: 12, counts: ['ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'EXPIRED'].map((status, index) => ({ status, count: [2, 3, 4, 2, 1][index] })) },
  activeCourses: [{ courseId: 70, title: '실제 보안 과정', type: 'OPTIONAL', enrollmentCount: 3, completedCount: 1, completionRate: 33.33 }],
  attention: { dueSoonCount: 4, failedCount: 2, expiredCount: 1 },
  recentCompletions: [{ enrollmentId: 9, memberName: '테스트 직원', courseTitle: '실제 보안 과정', completedAt: '2026-09-26T15:00:00' }],
  recentAssignments: [{ enrollmentId: 9, memberName: '테스트 직원', departmentName: null, courseTitle: '실제 보안 과정', status: 'COMPLETED', assignedAt: '2026-09-20T00:00:00' }],
}
await test('Dashboard는 집계 Endpoint를 한 번 호출하며 취소 signal을 전달', async () => {
  const calls = [], controller = new AbortController()
  apiClient.defaults.adapter = async config => { calls.push(config); return { config, data, status: 200, statusText: '', headers: {} } }
  assert.deepEqual(await dashboardApi.get(controller.signal), data)
  assert.equal(calls.length, 1); assert.equal(calls[0].url, '/admin/dashboard')
  assert.equal(calls[0].signal, controller.signal)
})
await test('Dashboard API 실패는 호출자에게 전달되어 오류·재시도 UI로 처리', async () => {
  apiClient.defaults.adapter = async () => { throw new Error('test failure') }
  await assert.rejects(dashboardApi.get())
})
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
try {
  const { DashboardView } = await server.ssrLoadModule('/src/pages/DashboardPage.tsx')
  const render = (value, error = '') => renderToStaticMarkup(h(MemoryRouter, null, h(DashboardView, { data: value, error, onRetry() {} })))
  await test('실제 Overview와 상태별 분포를 렌더링하고 분기·증감 Mock을 표시하지 않는다', () => {
    const html = render(data)
    assert.match(html, /전체 수료율/); assert.match(html, /33.33%/)
    assert.match(html, /<dd>17<span>명/); assert.match(html, /<dd>8<span>개/); assert.match(html, /<dd>5<span>건/)
    assert.match(html, /전체 12건/); assert.match(html, /배정됨<strong>2/)
    assert.doesNotMatch(html, /이번 분기|전 분기|%p|미리보기 데이터|248|186|82%/)
  })
  await test('운영 중 과정의 실제 수강 수·수료율 및 목록 경로를 표시', () => {
    const html = render(data)
    assert.match(html, /실제 보안 과정/); assert.match(html, /선택교육/); assert.match(html, /수강 3명/)
    assert.match(html, /aria-label="실제 보안 과정 수료율"/); assert.match(html, /href="\/admin\/courses"/)
    assert.doesNotMatch(html, /평균 진도/)
  })
  await test('확인 필요 수강과 최근 수료·배정은 서버 응답과 실제 시각 의미를 사용', () => {
    const html = render(data)
    assert.match(html, /오늘부터 7일 후까지 마감/); assert.match(html, /attention-count tone-warning">4/)
    assert.match(html, /테스트 직원/); assert.match(html, /2026-09-26T15:00:00Z/)
    assert.match(html, /최근 교육 배정/); assert.match(html, /현재 상태/); assert.match(html, /부서 미지정/)
    assert.match(html, /2026-09-20T00:00:00Z/)
  })
  await test('Loading에는 고정 숫자나 Mock 과정 없이 로딩만 표시', () => {
    const html = render(null)
    assert.match(html, /role="status"/); assert.match(html, /불러오는 중/)
    assert.doesNotMatch(html, /82|248|정보보안|수료율/)
  })
  await test('오류에는 재시도 버튼을 표시하고 이전 지표를 섞어 표시하지 않음', () => {
    const html = render(data, '대시보드를 불러오지 못했습니다.')
    assert.match(html, /role="alert"/); assert.match(html, /다시 시도/)
    assert.doesNotMatch(html, /33.33|테스트 직원/)
  })
  await test('0건은 0%와 각 Empty State를 표시하며 NaN/Infinity가 없다', () => {
    const empty = { overview: { employeeCount: 0, openCourseCount: 0, ongoingEnrollmentCount: 0, completionRate: 0 },
      distribution: { total: 0, counts: data.distribution.counts.map(row => ({ ...row, count: 0 })) },
      activeCourses: [], attention: { dueSoonCount: 0, failedCount: 0, expiredCount: 0 }, recentCompletions: [], recentAssignments: [] }
    const html = render(empty)
    assert.match(html, /전체 0건/); assert.match(html, /0%/)
    assert.match(html, /현재 운영 중인 교육이 없습니다/); assert.match(html, /최근 수료 내역이 없습니다/)
    assert.match(html, /최근 교육 배정 내역이 없습니다/); assert.doesNotMatch(html, /NaN|Infinity|undefined|null/)
  })
} finally { await server.close() }
