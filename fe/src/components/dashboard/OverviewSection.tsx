import type { Dashboard } from '../../api/dashboardApi'
import { CircularProgress } from '../common/Progress'
import { EnrollmentFlow } from './EnrollmentFlow'

export function OverviewSection({ overview, distribution }: Pick<Dashboard, 'overview' | 'distribution'>) {
  const metrics = [
    { label: '전체 직원', value: overview.employeeCount, unit: '명' },
    { label: '진행 중 교육', value: overview.openCourseCount, unit: '개' },
    { label: '진행 중 수강', value: overview.ongoingEnrollmentCount, unit: '건' },
  ]
  return (
    <section className="overview surface" aria-label="교육 운영 요약">
      <div className="overview-metrics">
        <div className="completion-metric">
          <CircularProgress value={overview.completionRate} label="전체 수료율" />
          <div>
            <h2 className="metric-label">전체 수료율</h2>
            <div className="headline-value">{overview.completionRate}%</div>
            <p className="metric-note">전체 수강 배정 대비 수료 비율입니다</p>
          </div>
        </div>
        <dl className="compact-metrics">
          {metrics.map((metric) => (
            <div key={metric.label}><dt>{metric.label}</dt><dd>{metric.value}<span>{metric.unit}</span></dd></div>
          ))}
        </dl>
      </div>
      <EnrollmentFlow distribution={distribution} />
    </section>
  )
}
