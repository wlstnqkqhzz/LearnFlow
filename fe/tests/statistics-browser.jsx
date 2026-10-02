// Test-only UI fixture: npm run dev -- --host 127.0.0.1, then /tests/statistics-browser.html.
// Uses the production page/hooks/client. No fixture is imported by the application build.
import { createRoot } from 'react-dom/client'
import { StrictMode, useState } from 'react'
import { BrowserRouter } from 'react-router-dom'
import { AxiosError } from 'axios'
import { apiClient } from '../src/api/client.ts'
import { StatisticsPage } from '../src/pages/admin/StatisticsPage.tsx'
import '../src/index.css'
import '../src/App.css'
import '../src/admin.css'

let failTrends = false
let releaseOld
const page = (content, index = 0) => ({ content, page: index, size: 20, totalElements: 21, totalPages: 2 })
apiClient.defaults.adapter = async config => {
  const params = config.params ?? {}
  const meta = { ...params, timeZone: 'Asia/Seoul', generatedAt: new Date().toISOString(), departmentScope: 'DIRECT', courseId: params.courseId ?? null, departmentId: params.departmentId ?? null }
  const course = { courseId: params.courseId ?? (params.page ? 21 : 7), title: params.page ? '두 번째 페이지 과정' : '정보보안 교육', status: 'OPEN', retrainingPolicyId: 2, occurrenceNumber: 3, assignedCount: 10, notStartedCount: 1, inProgressCount: 3, completedCount: 3, failedCount: 2, expiredCount: 1, completionRate: 30, exam: { examId: 9, submittedAttemptCount: 11, passedAttemptCount: 6, averageScore: 75.55, attemptPassRate: 54.55 } }
  let data
  if (config.url === '/courses') data = page([{ id: 7, title: '정보보안 교육' }, { id: 8, title: '직장 안전 교육' }])
  else if (config.url === '/departments') data = [{ id: 2, name: '개발팀', isActive: true }, { id: 3, name: '이전 조직', isActive: false }]
  else if (config.url.endsWith('/overview')) {
    data = { meta, assignedCount: params.courseId === 8 ? 88 : 10, completedCount: 21, cohortCompletedCount: 3, cohortFailedCount: 2, cohortExpiredCount: 1, completionRate: 30, averageCompletionDays: 4.25 }
    // Hold the old response until explicitly released, even if its signal is aborted.
    if (params.courseId === 7) await new Promise(resolve => { releaseOld = resolve })
  } else if (config.url.endsWith('/trends')) {
    if (failTrends) throw new AxiosError('fixture failure', undefined, config, undefined, { status: 500, data: { code: 'INTERNAL_SERVER_ERROR' } })
    const monthly = params.startDate < '2026-08-01'
    data = { meta, granularity: monthly ? 'MONTH' : 'DAY', points: [
      { bucketStart: params.startDate, periodStart: params.startDate, periodEnd: params.startDate, assignments: 2, completions: 9 },
      { bucketStart: params.endDate, periodStart: params.endDate, periodEnd: params.endDate, assignments: 12, completions: 5 },
    ] }
  } else if (config.url.endsWith('/courses')) data = { meta, data: page([course], params.page) }
  else if (config.url.endsWith('/departments')) data = { meta, data: page([{ departmentId: params.departmentId ?? 3, departmentName: '이전 조직', active: false, parentDepartmentId: 1, currentEmployeeCount: 4, assignedCount: 10, completedCount: 3, failedCount: 2, expiredCount: 1, completionRate: null }], params.page) }
  else throw new Error(`Unexpected test request: ${config.url}`)
  return { config, data, status: 200, statusText: '', headers: {} }
}
function Fixture() {
  const [width, setWidth] = useState('100%')
  const [failure, setFailure] = useState(false)
  return <><div className="admin-toolbar" style={{ padding: 12 }}><strong>테스트 응답 · 실제 데이터 아님</strong>
    <button onClick={() => { failTrends = !failTrends; setFailure(failTrends) }}>추세 오류 {failure ? 'ON' : 'OFF'}</button>
    <button onClick={() => releaseOld?.()}>늦은 과정 #7 응답 반환</button>
    <button onClick={() => setWidth(width === '100%' ? '375px' : '100%')}>폭 전환</button>
  </div><div style={{ width, maxWidth: '100%' }}><StatisticsPage /></div></>
}
createRoot(document.getElementById('root')).render(<StrictMode><BrowserRouter><Fixture /></BrowserRouter></StrictMode>)
