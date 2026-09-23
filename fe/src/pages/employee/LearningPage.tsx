import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { employeeLearningApi } from '../../api/employeeLearningApi.ts'
import type { EnrollmentStatus } from '../../api/assignmentTypes.ts'
import type { MyEnrollment, MyEnrollmentSearch } from '../../api/learningTypes.ts'
import { EmptyState, ErrorState, LoadingState } from '../../components/admin/AdminUI.tsx'
import { useRemote } from '../../hooks/useRemote.ts'
import { dueLabel, learningCourseTypes, learningPeriod, learningStatuses } from './learningUtils.ts'

export function LearningStatusBadge({ status }: { status: EnrollmentStatus }) {
  const value = learningStatuses[status]
  return <span className={`status-badge tone-${value.tone}`}>{value.label}</span>
}

export function EnrollmentCard({ enrollment }: { enrollment: MyEnrollment }) {
  const terminal = ['COMPLETED', 'FAILED', 'EXPIRED'].includes(enrollment.status)
  return <article className="surface learning-card"><div className="learning-card-main"><div className="learning-badges"><span className="status-badge tone-primary">{learningCourseTypes[enrollment.courseType]}</span><LearningStatusBadge status={enrollment.status} /></div><h2>{enrollment.courseTitle}</h2><p>운영 기간 {learningPeriod(enrollment.courseStartDate, enrollment.courseEndDate)}</p><p className="learning-due">{dueLabel(enrollment.dueDate)}</p></div><Link className={`admin-button${terminal ? '' : ' primary-button'}`} to={`/employee/learning/${enrollment.enrollmentId}`}>{terminal ? '교육 내용 보기' : enrollment.status === 'ASSIGNED' ? '학습 시작하기' : '계속 학습하기'}</Link></article>
}

export function LearningPage() {
  const [filters, setFilters] = useState<MyEnrollmentSearch>({ page: 0, size: 12 })
  const loader = useCallback((signal: AbortSignal) => employeeLearningApi.list(filters, signal), [filters])
  const query = useRemote(loader)
  const filtered = !!filters.status
  function changeStatus(status?: EnrollmentStatus) { setFilters(current => ({ ...current, status, page: 0 })) }
  return <div className="learning-page">
    <div className="learning-filter" role="group" aria-label="수강 상태 필터"><button className={`admin-button${!filters.status ? ' primary-button' : ''}`} onClick={() => changeStatus()}>전체</button>{Object.entries(learningStatuses).map(([status, value]) => <button key={status} className={`admin-button${filters.status === status ? ' primary-button' : ''}`} onClick={() => changeStatus(status as EnrollmentStatus)}>{value.label}</button>)}</div>
    {query.loading ? <LoadingState /> : query.error ? <ErrorState message={query.error} retry={query.reload} /> : !query.data?.content.length ? <EmptyState message={filtered ? '조건에 맞는 교육이 없습니다.' : '현재 배정된 교육이 없습니다.'}>{!filtered && <p className="admin-hint">새로운 교육이 배정되면 이곳에서 확인할 수 있습니다.</p>}</EmptyState> : <div className="learning-list">{query.data.content.map(enrollment => <EnrollmentCard key={enrollment.enrollmentId} enrollment={enrollment} />)}</div>}
    {query.data && query.data.totalPages > 1 && <div className="learning-pagination"><span>전체 {query.data.totalElements}개 · {query.data.page + 1} / {query.data.totalPages} 페이지</span><div className="row-actions"><button className="admin-button" disabled={query.data.page <= 0} onClick={() => setFilters(current => ({ ...current, page: current.page - 1 }))}>이전</button><button className="admin-button" disabled={query.data.page + 1 >= query.data.totalPages} onClick={() => setFilters(current => ({ ...current, page: current.page + 1 }))}>다음</button></div></div>}
  </div>
}
