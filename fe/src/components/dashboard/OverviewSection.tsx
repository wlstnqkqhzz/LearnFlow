import { overview } from '../../data/dashboardMockData'
import { CircularProgress } from '../common/Progress'
import { EnrollmentFlow } from './EnrollmentFlow'

export function OverviewSection() {
  return (
    <section className="overview surface" aria-label="교육 운영 요약">
      <div className="overview-metrics">
        <div className="completion-metric">
          <CircularProgress value={overview.completionRate} label="이번 분기 평균 수료율" />
          <div>
            <h2 className="metric-label">이번 분기 평균 수료율</h2>
            <div className="headline-value">{overview.completionRate}% <span className="metric-change">↗ +{overview.change}%p</span></div>
            <p className="metric-note">전 분기 대비 꾸준히 성장 중입니다</p>
          </div>
        </div>
        <dl className="compact-metrics">
          {overview.metrics.map((metric) => (
            <div key={metric.label}><dt>{metric.label}</dt><dd>{metric.value}<span>{metric.unit}</span></dd></div>
          ))}
        </dl>
      </div>
      <EnrollmentFlow />
    </section>
  )
}
