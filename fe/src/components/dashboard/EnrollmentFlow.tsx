import type { Dashboard } from '../../api/dashboardApi'
import { enrollmentStatus } from '../common/enrollmentStatus'

export function EnrollmentFlow({ distribution }: Pick<Dashboard, 'distribution'>) {
  const { total, counts: enrollmentFlow } = distribution
  return (
    <div className="enrollment-flow">
      <h2>교육 진행 현황</h2>
      <p className="section-description">전체 {total}건</p>
      <div className="segmented-progress" aria-hidden="true">
        {enrollmentFlow.map((item) => <span key={item.status} className={`segment tone-${enrollmentStatus[item.status].tone}`} style={{ width: `${total ? item.count / total * 100 : 0}%` }} />)}
      </div>
      <ul className="flow-legend" aria-label="교육 진행 상태별 건수">
        {enrollmentFlow.map((item) => (
          <li key={item.status}>
            <span className={`legend-dot tone-${enrollmentStatus[item.status].tone}`} aria-hidden="true" />
            {enrollmentStatus[item.status].label}<strong>{item.count}</strong>
          </li>
        ))}
      </ul>
    </div>
  )
}
