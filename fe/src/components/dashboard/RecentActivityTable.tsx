import { recentActivities } from '../../data/dashboardMockData'
import { StatusBadge } from '../common/StatusBadge'

export function RecentActivityTable() {
  return (
    <section className="recent-activity" aria-labelledby="activity-title">
      <div className="section-heading"><h2 id="activity-title">최근 수강 활동</h2><span className="mock-label">미리보기 데이터</span></div>
      <div className="table-scroll" role="region" aria-label="최근 수강 활동 표" tabIndex={0}>
        <table>
          <caption className="sr-only">직원별 최근 수강 활동 6건</caption>
          <thead><tr>{['직원', '부서', '교육과정', '상태', '일자'].map((label) => <th key={label} scope="col">{label}</th>)}</tr></thead>
          <tbody>{recentActivities.map((item) => (
            <tr key={item.id}><th scope="row">{item.name}</th><td>{item.department}</td><td>{item.course}</td><td><StatusBadge status={item.status} /></td><td><time dateTime={item.date}>{item.date.replaceAll('-', '.')}</time></td></tr>
          ))}</tbody>
        </table>
      </div>
    </section>
  )
}
