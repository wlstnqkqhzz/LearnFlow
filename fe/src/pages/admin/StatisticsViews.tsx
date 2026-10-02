import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { PageResponse } from '../../api/adminTypes.ts'
import type { StatisticsCourse, StatisticsDepartment, StatisticsOverview } from '../../api/statisticsTypes.ts'
import { ActiveBadge, EmptyState, ErrorState, LoadingState } from '../../components/admin/AdminUI.tsx'
import { CourseStatusBadge } from '../../components/admin/CourseBadges.tsx'
import { statisticNumber as number } from './statisticsUtils.ts'

export function StatisticsState({ loading, error, retry, children }: { loading: boolean; error: string; retry: () => void; children: ReactNode }) {
  return loading ? <LoadingState /> : error ? <ErrorState message={error} retry={retry} /> : <>{children}</>
}
export function StatisticsPagination({ data, onPage }: { data: Pick<PageResponse<unknown>, 'page' | 'totalPages' | 'totalElements'>; onPage: (page: number) => void }) {
  return <div className="admin-pagination"><span>전체 {number(data.totalElements)}개 · {data.totalPages ? data.page + 1 : 0} / {data.totalPages} 페이지</span><div className="row-actions">
    <button className="admin-button" type="button" disabled={data.page <= 0} onClick={() => onPage(data.page - 1)}>이전</button>
    <button className="admin-button" type="button" disabled={data.page + 1 >= data.totalPages} onClick={() => onPage(data.page + 1)}>다음</button>
  </div></div>
}
export function StatisticsOverviewView({ data }: { data: StatisticsOverview }) {
  return <>
    <dl className="statistics-kpis">
      <div><dt>기간 내 배정 건수</dt><dd>{number(data.assignedCount)}<small>건</small></dd><p>선택 기간에 배정된 교육</p></div>
      <div><dt>기간 내 수료 건수</dt><dd>{number(data.completedCount)}<small>건</small></dd><p>이전 배정 포함 · 기간 내 수료 발생</p></div>
      <div><dt>배정 집단 수료율</dt><dd>{number(data.completionRate, '%')}</dd><p>기간 내 배정 중 현재 수료 비율</p></div>
      <div><dt>평균 배정 후 수료 소요일</dt><dd>{number(data.averageCompletionDays, '일')}</dd><p>기간 내 수료 건의 배정부터 수료까지</p></div>
    </dl>
    {data.assignedCount === 0 && data.completedCount === 0 && <EmptyState message="선택 기간에 배정·수료 실적이 없습니다." />}
    <h3>배정 집단 현재 상태</h3>
    <p className="admin-hint">선택 기간에 배정된 교육의 현재 상태입니다. 조회 종료일 당시의 상태가 아닙니다.</p>
    <dl className="statistics-cohort">
      <div><dt>학습 전</dt><dd>— <small>집계 미제공</small></dd></div>
      <div><dt>학습 중</dt><dd>— <small>집계 미제공</small></dd></div>
      <div><dt>수료</dt><dd>{number(data.cohortCompletedCount)}건</dd></div>
      <div><dt>실패</dt><dd>{number(data.cohortFailedCount)}건</dd></div>
      <div><dt>만료</dt><dd>{number(data.cohortExpiredCount)}건</dd></div>
    </dl>
    <p className="admin-hint">전체 학습 전·학습 중 집계는 현재 API에서 제공하지 않습니다. 과정별 현황은 아래 표에서 확인할 수 있습니다. ‘—’는 대상 없음 또는 집계 미제공을 뜻합니다.</p>
  </>
}
export function StatisticsCourseTable({ rows }: { rows: StatisticsCourse[] }) {
  if (!rows.length) return <EmptyState message="조회 조건에 맞는 과정이 없습니다." />
  return <div className="table-scroll" role="region" aria-label="과정별 통계 표" tabIndex={0}><table className="admin-table statistics-course-table"><thead><tr>
    {['과정명', '상태', '회차', '배정', '학습 전', '학습 중', '수료', '실패', '만료', '수료율', '시험 통계'].map(label => <th scope="col" key={label}>{label}</th>)}
  </tr></thead><tbody>{rows.map(row => <tr key={row.courseId}>
    <th scope="row"><Link to={`/admin/courses/${row.courseId}`}>{row.title}</Link></th><td><CourseStatusBadge status={row.status} /></td>
    <td>{row.occurrenceNumber === null ? '—' : `${row.occurrenceNumber}회차`}{row.retrainingPolicyId !== null && <small className="table-secondary">정책 #{row.retrainingPolicyId}</small>}</td>
    {[row.assignedCount, row.notStartedCount, row.inProgressCount, row.completedCount, row.failedCount, row.expiredCount].map((count, index) => <td key={index}>{number(count)}</td>)}
    <td>{number(row.completionRate, '%')}</td><td>{row.exam === null ? <span className="admin-hint">시험 없음</span> : <details><summary aria-label={`${row.title} 시험 통계`}>시험 상세</summary>
      <dl className="statistics-exam"><div><dt>제출 응시 수</dt><dd>{number(row.exam.submittedAttemptCount)}건</dd></div><div><dt>합격 응시 수</dt><dd>{number(row.exam.passedAttemptCount)}건</dd></div><div><dt>평균 점수</dt><dd>{number(row.exam.averageScore)}</dd></div><div><dt>응시 합격률</dt><dd>{number(row.exam.attemptPassRate, '%')}</dd></div></dl>
      {row.exam.submittedAttemptCount === 0 && <small>기간 내 제출 응시 없음</small>}
    </details>}</td>
  </tr>)}</tbody></table></div>
}
export function StatisticsDepartmentTable({ rows }: { rows: StatisticsDepartment[] }) {
  if (!rows.length) return <EmptyState message="조회 조건에 맞는 부서가 없습니다." />
  return <div className="table-scroll" role="region" aria-label="부서별 통계 표" tabIndex={0}><table className="admin-table statistics-department-table"><thead><tr>
    {['부서명', '활성 상태', '현재 직원 수', '배정', '수료', '실패', '만료', '수료율'].map(label => <th scope="col" key={label}>{label}</th>)}
  </tr></thead><tbody>{rows.map(row => <tr key={row.departmentId}><th scope="row">{row.departmentName}</th><td><ActiveBadge active={row.active} /></td>
    {[row.currentEmployeeCount, row.assignedCount, row.completedCount, row.failedCount, row.expiredCount].map((count, index) => <td key={index}>{number(count)}</td>)}<td>{number(row.completionRate, '%')}</td>
  </tr>)}</tbody></table></div>
}
