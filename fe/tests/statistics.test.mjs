import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFile } from 'node:fs/promises'
import { Children, createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { createServer } from 'vite'
import { apiClient } from '../src/api/client.ts'
import { statisticsApi } from '../src/api/statisticsApi.ts'
import { presetDates, readStatisticsQuery, seoulToday, statisticsQuery, statisticsValidation } from '../src/pages/admin/statisticsUtils.ts'

const today = '2026-10-02'
const filter = { startDate: '2026-09-03', endDate: today, courseId: 7, departmentId: 2 }
const meta = { ...filter, timeZone: 'Asia/Seoul', departmentScope: 'DIRECT', generatedAt: '2026-10-02T01:00:00Z' }
const overview = { meta, assignedCount: 10, completedCount: 21, cohortCompletedCount: 3, cohortFailedCount: 2, cohortExpiredCount: 1, completionRate: 30, averageCompletionDays: 4.25 }
const exam = { examId: 9, submittedAttemptCount: 11, passedAttemptCount: 6, averageScore: 75.55, attemptPassRate: 54.55 }
const course = { courseId: 7, title: '보안 교육', status: 'OPEN', retrainingPolicyId: 2, occurrenceNumber: 3, assignedCount: 10, notStartedCount: 1, inProgressCount: 3, completedCount: 3, failedCount: 2, expiredCount: 1, completionRate: 30, exam }
const department = { departmentId: 2, departmentName: '개발팀', parentDepartmentId: 1, active: false, currentEmployeeCount: 4, assignedCount: 10, completedCount: 3, failedCount: 2, expiredCount: 1, completionRate: 30 }
const point = { bucketStart: '2026-09-03', periodStart: '2026-09-03', periodEnd: '2026-09-03', assignments: 2, completions: 9 }
const noop = () => {}

await test('기본 최근 30일은 서울 기준 오늘 포함 30일; UTC 자정과 독립', () => {
  assert.equal(seoulToday(new Date('2026-10-01T14:59:59Z')), '2026-10-01')
  assert.equal(seoulToday(new Date('2026-10-01T15:00:00Z')), today)
  assert.deepEqual(readStatisticsQuery(new URLSearchParams(), today).filter, { startDate: '2026-09-03', endDate: today })
  assert.equal(readStatisticsQuery(new URLSearchParams(), today).preset, 'days30')
})
await test('3개월/6개월/올해 preset, 월말·윤년 날짜 보정', () => {
  assert.deepEqual(presetDates('months3', today), { startDate: '2026-07-03', endDate: today })
  assert.deepEqual(presetDates('months6', today), { startDate: '2026-04-03', endDate: today })
  assert.deepEqual(presetDates('year', today), { startDate: '2026-01-01', endDate: today })
  assert.equal(presetDates('months6', '2024-08-31').startDate, '2024-03-01')
  assert.equal(presetDates('months3', '2024-05-31').startDate, '2024-03-01')
  assert.equal(presetDates('days30', '2024-03-01').startDate, '2024-02-01')
  assert.deepEqual(readStatisticsQuery(new URLSearchParams('preset=months6'), today).filter, presetDates('months6', today))
})
await test('직접 날짜·Course/Department·페이지 URL 왕복, 필터 재적용 시 페이지 초기화', () => {
  const query = statisticsQuery(filter, 'custom', 2, 3)
  assert.deepEqual(readStatisticsQuery(query, today), { filter, preset: 'custom', coursePage: 2, departmentPage: 3 })
  assert.deepEqual(readStatisticsQuery(new URLSearchParams(query.toString()), today).filter, filter)
  assert.equal(statisticsQuery(filter, 'months3').has('coursePage'), false)
  assert.equal(statisticsQuery(filter, 'months3').has('departmentPage'), false)
  assert.equal(readStatisticsQuery(new URLSearchParams('coursePage=-1&departmentPage=1.2'), today).coursePage, 0)
  assert.equal(readStatisticsQuery(new URLSearchParams('startDate=2026-01-01'), today).preset, 'custom')
})
await test('날짜 및 ID 검증: 366일 포함 허용, 초과·미래·잘못된 날짜 거부', () => {
  assert.equal(statisticsValidation({ startDate: '2024-01-01', endDate: '2024-12-31' }, today), '')
  for (const value of [
    { startDate: '2024-01-01', endDate: '2025-01-01' }, { startDate: '2026-02-29', endDate: today },
    { startDate: '2026-04-31', endDate: today }, { startDate: '', endDate: today },
    { startDate: today, endDate: '2026-10-01' }, { startDate: today, endDate: '2026-10-03' },
    { ...filter, courseId: 0 }, { ...filter, departmentId: NaN },
  ]) assert.ok(statisticsValidation(value, today), JSON.stringify(value))
  assert.ok(statisticsValidation(readStatisticsQuery(new URLSearchParams('courseId=garbage'), today).filter, today))
})
await test('4개 실제 Endpoint/params/signal/page 계약, 응답 숫자 재계산 없음', async () => {
  const previous = apiClient.defaults.adapter, calls = [], signal = new AbortController().signal
  apiClient.defaults.adapter = async config => { calls.push(config); return { config, data: overview, status: 200, statusText: '', headers: {} } }
  try {
    assert.deepEqual(await statisticsApi.overview(filter, signal), overview)
    await statisticsApi.trends(filter, signal); await statisticsApi.courses(filter, 2, signal); await statisticsApi.departments(filter, 3, signal)
    assert.deepEqual(calls.map(call => call.url), ['overview', 'trends', 'courses', 'departments'].map(path => `/admin/statistics/${path}`))
    assert.deepEqual(calls.map(call => call.method), ['get', 'get', 'get', 'get'])
    assert.deepEqual(calls[0].params, filter); assert.deepEqual(calls[1].params, filter)
    assert.deepEqual(calls[2].params, { ...filter, page: 2, size: 20 }); assert.deepEqual(calls[3].params, { ...filter, page: 3, size: 20 })
    assert.ok(calls.every(call => call.signal === signal))
  } finally { apiClient.defaults.adapter = previous }
})
await test('취소된 이전 필터 응답은 Axios 단계에서도 거부되고 새 결과만 성공', async () => {
  const previous = apiClient.defaults.adapter
  let resolveOld, started
  const ready = new Promise(resolve => { started = resolve })
  apiClient.defaults.adapter = config => config.params.courseId === 1 ? new Promise(resolve => { resolveOld = () => resolve({ config, data: { ...overview, assignedCount: 999 }, status: 200, headers: {} }); started() }) : Promise.resolve({ config, data: overview, status: 200, headers: {} })
  try {
    const oldController = new AbortController()
    const oldRequest = statisticsApi.overview({ ...filter, courseId: 1 }, oldController.signal)
    const rejected = assert.rejects(oldRequest, { code: 'ERR_CANCELED' })
    await ready; oldController.abort()
    assert.deepEqual(await statisticsApi.overview(filter, new AbortController().signal), overview)
    resolveOld(); await rejected
  } finally { apiClient.defaults.adapter = previous }
})

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
try {
  const ui = await server.ssrLoadModule('/src/pages/admin/StatisticsViews.tsx')
  const { StatisticsChart } = await server.ssrLoadModule('/src/pages/admin/StatisticsChart.tsx')
  const { StatisticsFilters } = await server.ssrLoadModule('/src/pages/admin/StatisticsFilters.tsx')
  const { StatisticsPage } = await server.ssrLoadModule('/src/pages/admin/StatisticsPage.tsx')
  const { AuthContext } = await server.ssrLoadModule('/src/auth/AuthContext.ts')
  const { RoleRoute } = await server.ssrLoadModule('/src/routes/RoleRoute.tsx')
  const { AppSidebar } = await server.ssrLoadModule('/src/components/layout/AppSidebar.tsx')
  const { default: App } = await server.ssrLoadModule('/src/App.tsx')
  const render = (element, path = '/admin/statistics') => renderToStaticMarkup(h(MemoryRouter, { initialEntries: [path] }, element))
  await test('Overview의 수료 발생·cohort 수료율 구분, 서버값 직접 표시', () => {
    const html = render(h(ui.StatisticsOverviewView, { data: overview }))
    for (const text of ['기간 내 배정 건수', '기간 내 수료 건수', '이전 배정 포함', '30%', '4.25일', '<dd>21', '집계 미제공', '3건', '2건', '1건']) assert.ok(html.includes(text), text)
    assert.doesNotMatch(html, /210%/)
  })
  await test('null 비율·평균은 —, 실제 0은 0, 실적 없는 기간 Empty', () => {
    const html = render(h(ui.StatisticsOverviewView, { data: { ...overview, assignedCount: 0, completedCount: 0, completionRate: null, averageCompletionDays: null } }))
    assert.match(html, /<dd>—<\/dd>/); assert.doesNotMatch(html, /0%|0일/); assert.match(html, /실적이 없습니다/)
    assert.match(render(h(ui.StatisticsOverviewView, { data: { ...overview, completionRate: 0, averageCompletionDays: 0 } })), /0%/)
  })
  await test('DAY/MONTH 추세: 서버 구간·건수, 두 계열, 축/범례/키보드/대체 표', () => {
    for (const granularity of ['DAY', 'MONTH']) {
      const data = { meta, granularity, points: [{ ...point, periodEnd: granularity === 'MONTH' ? '2026-09-30' : point.periodEnd }] }
      const html = render(h(StatisticsChart, { data }))
      assert.match(html, /viewBox="0 0 740 270"/); assert.equal((html.match(/<polyline/g) ?? []).length, 2)
      assert.match(html, /추세 데이터 표/); assert.match(html, /tabindex="0"/); assert.match(html, /배정 2건, 수료 9건/)
      assert.match(html, /건수/); assert.match(html, granularity === 'DAY' ? /일별/ : /월별/)
      assert.ok(html.includes(data.points[0].periodEnd)); assert.doesNotMatch(html, /실패|만료/)
    }
  })
  await test('빈 추세와 서버 zero-fill 추세는 Empty; zero 데이터 표 유지', () => {
    for (const points of [[], [{ ...point, assignments: 0, completions: 0 }]]) {
      const html = render(h(StatisticsChart, { data: { meta, granularity: 'DAY', points } }))
      assert.match(html, /추세 데이터가 없습니다/); assert.doesNotMatch(html, /NaN|Infinity|<svg/)
      assert.equal(html.includes('추세 데이터 표'), points.length > 0)
    }
  })
  await test('과정별 5상태·회차·시험 응시 표시, 시험 없음/미제출/null 처리', () => {
    const html = render(h(ui.StatisticsCourseTable, { rows: [course, { ...course, courseId: 8, occurrenceNumber: 4, exam: null, completionRate: null }, { ...course, courseId: 9, exam: { ...exam, submittedAttemptCount: 0, passedAttemptCount: 0, averageScore: null, attemptPassRate: null } }] }))
    for (const text of ['학습 전', '학습 중', '3회차', '4회차', '시험 상세', '11건', '75.55', '54.55%', '시험 없음', '제출 응시 없음', '<td>—</td>']) assert.ok(html.includes(text), text)
    assert.match(html, /href="\/admin\/courses\/7"/)
    assert.match(render(h(ui.StatisticsCourseTable, { rows: [] })), /과정이 없습니다/)
  })
  await test('부서별 실적·현재 직원 수·비활성 부서·null 비율·Empty', () => {
    const html = render(h(ui.StatisticsDepartmentTable, { rows: [department, { ...department, departmentId: 3, active: true, completionRate: null }] }))
    for (const text of ['개발팀', '비활성', '현재 직원 수', '<td>4</td>', '30%', '<td>—</td>']) assert.ok(html.includes(text), text)
    assert.match(render(h(ui.StatisticsDepartmentTable, { rows: [] })), /부서가 없습니다/)
  })
  await test('서버 페이지네이션의 전체 건수·페이지·이전/다음 경계', () => {
    const props = { data: { page: 1, totalPages: 3, totalElements: 45 }, onPage: noop }
    const html = render(h(ui.StatisticsPagination, props))
    assert.match(html, /45개 · 2 \/ 3/); assert.doesNotMatch(html, /disabled/)
    assert.equal((render(h(ui.StatisticsPagination, { ...props, data: { page: 0, totalPages: 0, totalElements: 0 } })).match(/disabled=""/g) ?? []).length, 2)
  })
  await test('섹션별 Loading/Error/Retry는 이전 데이터를 가리고 성공시 표시', () => {
    const props = { loading: false, error: '', retry: noop, children: h('p', null, '서버 결과') }
    assert.match(render(h(ui.StatisticsState, props)), /서버 결과/)
    assert.match(render(h(ui.StatisticsState, { ...props, loading: true })), /불러오고/)
    const html = render(h(ui.StatisticsState, { ...props, error: '조회 실패' }))
    assert.match(html, /role="alert"/); assert.match(html, /다시 시도/); assert.doesNotMatch(html, /서버 결과/)
  })
  await test('필터 폼 preset/직접 날짜/과정/부서/URL 초기값, 4개 독립 로딩', () => {
    const html = render(h(StatisticsFilters, { initial: filter, initialPreset: 'custom', onApply: noop }))
    for (const text of ['최근 30일', '최근 3개월', '최근 6개월', '올해', '직접 날짜 선택', '2026-09-03', '2026-10-02', '과정 #7', '부서 #2', '최대 366일']) assert.ok(html.includes(text), text)
    const page = render(h(StatisticsPage), `/admin/statistics?${statisticsQuery(filter, 'custom')}`)
    assert.match(page, /적용 기간: 2026-09-03 ~ 2026-10-02/)
    assert.match(page, /현재 직속 부서 기준 · 하위 부서 미포함/)
    assert.equal((page.match(/데이터를 불러오고 있습니다/g) ?? []).length, 5) // 4 sections + course picker
    assert.match(render(h(StatisticsPage), '/admin/statistics?startDate=bad&endDate=bad'), /올바른 시작일/)
  })
  await test('실제 Route는 ADMIN 하위, 메뉴·직접 경로는 비ADMIN에게 비노출', () => {
    const paths = []
    function visit(element, roles = []) {
      if (!element?.props) return
      const next = element.props.element?.type === RoleRoute ? [...roles, element.props.element.props.role] : roles
      if (element.props.path === '/admin/statistics') paths.push(next)
      Children.forEach(element.props.children, child => visit(child, next))
    }
    visit(App()); assert.deepEqual(paths, [['ADMIN']])
    for (const role of ['ADMIN', 'EMPLOYEE', 'INSTRUCTOR']) {
      const wrap = child => h(AuthContext.Provider, { value: { user: { roles: [role], email: 'test@local' }, isAuthenticated: true, isInitializing: false, logout: noop } }, child)
      assert.equal(render(wrap(h(AppSidebar))).includes('교육 통계'), role === 'ADMIN')
      const route = h(Routes, null, h(Route, { element: h(RoleRoute, { role: 'ADMIN' }) }, h(Route, { path: '*', element: h('p', null, 'STATISTICS_PRIVATE') })))
      assert.equal(render(wrap(route)).includes('STATISTICS_PRIVATE'), role === 'ADMIN')
    }
  })
  await test('Responsive: 표 지역은 키보드 스크롤, SVG 가변 폭, 좁은 KPI/필터 wrap', async () => {
    const css = await readFile(new URL('../src/pages/admin/statistics.css', import.meta.url), 'utf8')
    assert.match(css, /overflow-x: auto/); assert.match(css, /width: 100%; height: auto/)
    assert.match(css, /max-width: 600px/); assert.match(css, /grid-template-columns: minmax\(0, 1fr\)/)
    assert.match(css, /flex: 1 1 140px/)
    assert.match(render(h(ui.StatisticsCourseTable, { rows: [course] })), /role="region" aria-label="과정별 통계 표" tabindex="0"/)
  })
} finally { await server.close() }
