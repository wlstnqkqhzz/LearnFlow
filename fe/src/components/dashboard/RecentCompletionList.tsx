import { recentCompletions } from '../../data/dashboardMockData'
import { StatusBadge } from '../common/StatusBadge'

export function RecentCompletionList() {
  return (
    <section aria-labelledby="completion-title">
      <div className="section-heading"><h2 id="completion-title">최근 수료</h2></div>
      <ul className="completion-list surface">
        {recentCompletions.map((item) => (
          <li key={item.id}>
            <span className="avatar avatar-neutral" aria-hidden="true">{item.name[0]}</span>
            <div className="completion-copy"><h3>{item.name}</h3><p>{item.course}</p></div>
            <StatusBadge status="COMPLETED" />
          </li>
        ))}
      </ul>
    </section>
  )
}
