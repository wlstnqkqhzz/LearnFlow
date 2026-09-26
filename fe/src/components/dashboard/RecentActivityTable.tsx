import type { Dashboard } from '../../api/dashboardApi'
import { utcDateInSeoul } from '../../pages/employee/learningUtils'
import { StatusBadge } from '../common/StatusBadge'

export function RecentActivityTable({ recentAssignments }: Pick<Dashboard, 'recentAssignments'>) {
  return (
    <section className="recent-activity" aria-labelledby="activity-title">
      <div className="section-heading"><h2 id="activity-title">최근 교육 배정</h2><span className="section-description">배정일 기준 · 현재 상태</span></div>
      <div className="table-scroll" role="region" aria-label="최근 교육 배정 표" tabIndex={0}>
        <table>
          <caption className="sr-only">직원별 최근 교육 배정 최대 6건</caption>
          <thead><tr>{['직원', '부서', '교육과정', '현재 상태', '배정일'].map((label) => <th key={label} scope="col">{label}</th>)}</tr></thead>
          <tbody>{recentAssignments.length === 0 && <tr><td colSpan={5}>최근 교육 배정 내역이 없습니다.</td></tr>}{recentAssignments.map((item) => (
            <tr key={item.enrollmentId}><th scope="row">{item.memberName}</th><td>{item.departmentName ?? '부서 미지정'}</td><td>{item.courseTitle}</td><td><StatusBadge status={item.status} /></td><td><time dateTime={`${item.assignedAt}Z`}>{utcDateInSeoul(item.assignedAt)}</time></td></tr>
          ))}</tbody>
        </table>
      </div>
    </section>
  )
}
