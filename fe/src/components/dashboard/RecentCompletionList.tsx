import type { Dashboard } from '../../api/dashboardApi'
import { utcDateInSeoul } from '../../pages/employee/learningUtils'
import { StatusBadge } from '../common/StatusBadge'

export function RecentCompletionList({ recentCompletions }: Pick<Dashboard, 'recentCompletions'>) {
  return (
    <section aria-labelledby="completion-title">
      <div className="section-heading"><h2 id="completion-title">최근 수료</h2></div>
      <ul className="completion-list surface">
        {recentCompletions.length === 0 && <li>최근 수료 내역이 없습니다.</li>}
        {recentCompletions.map((item) => (
          <li key={item.enrollmentId}>
            <span className="avatar avatar-neutral" aria-hidden="true">{item.memberName[0]}</span>
            <div className="completion-copy"><h3>{item.memberName}</h3><p>{item.courseTitle}</p><time dateTime={`${item.completedAt}Z`}>{utcDateInSeoul(item.completedAt)}</time></div>
            <StatusBadge status="COMPLETED" />
          </li>
        ))}
      </ul>
    </section>
  )
}
