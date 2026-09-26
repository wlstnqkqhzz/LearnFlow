import { useCallback, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { courseApi } from '../../api/courseApi.ts'
import type { CourseStatus } from '../../api/courseTypes.ts'
import { CourseStatusBadge, CourseTypeBadge } from '../../components/admin/CourseBadges.tsx'
import { ErrorState, Feedback, LoadingState, PageHeader } from '../../components/admin/AdminUI.tsx'
import { ConfirmDialog } from '../../components/admin/Modal.tsx'
import { useAction } from '../../hooks/useAction.ts'
import { useRemote } from '../../hooks/useRemote.ts'
import { CourseContentsSection } from './CourseContentsSection.tsx'
import { CourseForm } from './CourseForm.tsx'
import { coursePeriod, nextCourseStatus, openValidation } from './courseUtils.ts'
import { AssignmentRulesSection } from './AssignmentRulesSection.tsx'
import { CourseExamSection } from './CourseExamSection.tsx'

export function CourseDetailPage() {
  const { courseId } = useParams()
  const id = Number(courseId)
  if (!Number.isSafeInteger(id) || id <= 0) return <div className="management-page"><p role="alert">올바른 교육과정 주소가 아닙니다.</p><Link to="/admin/courses">교육과정 목록으로</Link></div>
  return <CourseDetail key={id} id={id} />
}

function CourseDetail({ id }: { id: number }) {
  const loader = useCallback((signal: AbortSignal) => courseApi.get(id, signal), [id])
  const query = useRemote(loader)
  const action = useAction()
  const location = useLocation()
  const [confirmStatus, setConfirmStatus] = useState<Exclude<CourseStatus, 'DRAFT'> | null>(null)
  const [statusNotice, setStatusNotice] = useState('')
  const course = query.data
  const next = course ? nextCourseStatus(course.status) : null

  function requestStatus() {
    if (!course || !next) return
    const validation = next === 'OPEN' ? openValidation(course) : ''
    setStatusNotice(validation)
    if (!validation) { action.clear(); setConfirmStatus(next) }
  }
  async function changeStatus() {
    if (!confirmStatus) return
    const changed = await action.run(async () => { await courseApi.status(id, confirmStatus) }, confirmStatus === 'OPEN' ? '교육과정이 오픈되었습니다.' : '교육과정이 종료되었습니다.')
    if (changed) { setConfirmStatus(null); query.reload() }
  }

  return <div className="management-page">
    <PageHeader title="교육과정 상세" description="기본 정보, 콘텐츠, 배정 규칙과 시험을 관리합니다."><Link className="admin-button" to="/admin/courses">목록으로</Link></PageHeader>
    {location.state?.created && <p className="admin-success" role="status">교육과정이 초안으로 등록되었습니다.</p>}
    {query.loading ? <LoadingState /> : query.error ? <ErrorState message={query.error} retry={query.reload} /> : course && <>
      <section className="course-summary surface"><div><div className="row-actions"><CourseTypeBadge type={course.courseType} /><CourseStatusBadge status={course.status} /></div><h2>{course.title}</h2><p>{course.instructorName ?? '강사 미지정'} · {coursePeriod(course)}</p></div>{next && <button type="button" className={`admin-button ${next === 'OPEN' ? 'primary-button' : 'danger-button'}`} disabled={action.pending} onClick={requestStatus}>{next === 'OPEN' ? '과정 오픈' : '과정 종료'}</button>}</section>
      <Feedback error={statusNotice || (!confirmStatus ? action.error : '')} success={action.success} />
      <section className="surface detail-section course-editor" aria-labelledby="course-info-heading"><div className="section-heading"><div><h2 id="course-info-heading">기본 정보</h2><p className="admin-hint">교육과정의 이름, 설명, 기간과 수료 기준을 관리합니다.</p></div></div><CourseForm key={course.updatedAt} course={course} pending={action.pending} actionError="" submitLabel="기본 정보 저장" onSubmit={async request => {
        setStatusNotice('')
        await action.run(async () => { await courseApi.update(id, request); query.reload() }, '교육과정 정보를 수정했습니다.')
      }} /></section>
      <CourseContentsSection courseId={id} />
      <AssignmentRulesSection courseId={id} courseStatus={course.status} />
      <CourseExamSection courseId={id} />
      {confirmStatus && <ConfirmDialog title={confirmStatus === 'OPEN' ? `“${course.title}” 과정 오픈` : `“${course.title}” 과정 종료`} description={confirmStatus === 'OPEN' ? '과정을 오픈하면 설정된 배정 규칙에 따라 대상 직원에게 교육이 자동 배정될 수 있습니다.' : '종료 후에는 운영 중 상태로 되돌릴 수 없습니다.'} label={confirmStatus === 'OPEN' ? '과정 오픈' : '과정 종료'} tone={confirmStatus === 'OPEN' ? 'primary' : 'danger'} pending={action.pending} error={action.error} onClose={() => setConfirmStatus(null)} onConfirm={() => void changeStatus()} />}
    </>}
  </div>
}
