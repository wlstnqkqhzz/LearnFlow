import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { courseApi } from '../../api/courseApi.ts'
import { useAuth } from '../../auth/AuthContext.ts'
import { PageHeader, ErrorState, LoadingState } from '../../components/admin/AdminUI.tsx'
import { CourseStatusBadge } from '../../components/admin/CourseBadges.tsx'
import { useRemote } from '../../hooks/useRemote.ts'
import { CourseTable } from '../admin/CoursesPage.tsx'
import { CourseAssignmentsSection } from './CourseAssignmentsSection.tsx'
import { CourseworkState } from './CourseworkUI.tsx'
import { canManageAssignments } from './courseworkUtils.ts'

export function InstructorCoursesPage() {
  const { user } = useAuth()
  const [page, setPage] = useState(0)
  const query = useRemote(useCallback((signal: AbortSignal) => courseApi.list({ page, size: 20, instructorId: user!.memberId }, signal), [page, user]))
  return <div className="management-page"><PageHeader title="담당 과정 / 과제" description="담당 과정의 과제를 구성하고 직원 제출물을 채점합니다." />
    <section className="surface admin-table-surface"><CourseworkState loading={query.loading} error={query.error} empty={!query.data?.content.length} emptyMessage="담당하는 교육과정이 없습니다." retry={query.reload}>
      {query.data && <CourseTable courses={query.data.content} actionLabel="과제 관리" actionPath={course => `/instructor/courses/${course.id}/assignments`} />}
    </CourseworkState>
    {query.data && <div className="admin-pagination"><span>전체 {query.data.totalElements}개</span><div className="row-actions"><button className="admin-button" disabled={page <= 0} onClick={() => setPage(page - 1)}>이전</button><button className="admin-button" disabled={page + 1 >= query.data.totalPages} onClick={() => setPage(page + 1)}>다음</button></div></div>}
    </section>
  </div>
}
export function InstructorCourseworkPage() {
  const { courseId } = useParams()
  const id = Number(courseId)
  if (!Number.isSafeInteger(id) || id <= 0) return <p role="alert">올바른 교육과정 주소가 아닙니다.</p>
  return <InstructorCoursework key={id} courseId={id} />
}
function InstructorCoursework({ courseId }: { courseId: number }) {
  const { user } = useAuth()
  const query = useRemote(useCallback((signal: AbortSignal) => courseApi.get(courseId, signal), [courseId]))
  return <div className="management-page"><PageHeader title="과제 관리" description="과제를 구성하고 제출물을 조회·채점합니다."><Link className="admin-button" to="/instructor/courses">담당 과정 목록</Link></PageHeader>
    {query.loading ? <LoadingState /> : query.error ? <ErrorState message={query.error} retry={query.reload} /> : query.data && (canManageAssignments(user, query.data) ? <>
      <section className="surface detail-section coursework-stack"><h2 className="coursework-copy">{query.data.title}</h2><div><CourseStatusBadge status={query.data.status} /></div></section>
      <CourseAssignmentsSection course={query.data} />
    </> : <p role="alert">이 과정의 과제를 관리할 권한이 없습니다.</p>)}
  </div>
}
