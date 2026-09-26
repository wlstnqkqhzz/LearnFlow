import { OverviewSection } from '../components/dashboard/OverviewSection'
import { ActiveCourseList } from '../components/dashboard/ActiveCourseList'
import { AttentionPanel } from '../components/dashboard/AttentionPanel'
import { RecentCompletionList } from '../components/dashboard/RecentCompletionList'
import { RecentActivityTable } from '../components/dashboard/RecentActivityTable'
import { useEffect, useState } from 'react'
import { dashboardApi, type Dashboard } from '../api/dashboardApi'

export function DashboardPage() {
  const [data, setData] = useState<Dashboard | null>(null)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    dashboardApi.get(controller.signal).then(value => {
      if (!controller.signal.aborted) setData(value)
    }).catch(() => {
      if (!controller.signal.aborted) setError('대시보드를 불러오지 못했습니다. 다시 시도해 주세요.')
    })
    return () => controller.abort()
  }, [attempt])
  return <DashboardView data={data} error={error} onRetry={() => { setError(''); setData(null); setAttempt(value => value + 1) }} />
}

export function DashboardView({ data, error, onRetry }: { data: Dashboard | null; error: string; onRetry: () => void }) {
  if (error) return <div className="surface admin-feedback" role="alert"><p>{error}</p><button className="admin-button" type="button" onClick={onRetry}>다시 시도</button></div>
  if (!data) return <div className="surface admin-feedback" role="status">대시보드를 불러오는 중…</div>
  return (
    <div className="dashboard">
      <OverviewSection overview={data.overview} distribution={data.distribution} />
      <div className="dashboard-columns"><ActiveCourseList activeCourses={data.activeCourses} /><div className="dashboard-aside"><AttentionPanel attention={data.attention} /><RecentCompletionList recentCompletions={data.recentCompletions} /></div></div>
      <RecentActivityTable recentAssignments={data.recentAssignments} />
    </div>
  )
}
