import { useCallback, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { courseApi } from '../../api/courseApi.ts'
import { enrollmentApi } from '../../api/enrollmentApi.ts'
import { memberApi } from '../../api/memberApi.ts'
import type { Enrollment, EnrollmentSearch, EnrollmentStatus } from '../../api/assignmentTypes.ts'
import type { Course } from '../../api/courseTypes.ts'
import { EmptyState, ErrorState, Feedback, LoadingState, PageHeader, SubmitButton } from '../../components/admin/AdminUI.tsx'
import { Modal } from '../../components/admin/Modal.tsx'
import { useAction } from '../../hooks/useAction.ts'
import { useRemote } from '../../hooks/useRemote.ts'
import { assignmentSources, eligibleForManualAssignment, enrollmentStatuses } from './assignmentUtils.ts'

export function EnrollmentStatusBadge({ status }: { status: EnrollmentStatus }) { const value = enrollmentStatuses[status]; return <span className={`status-badge tone-${value.tone}`}>{value.label}</span> }
export function EnrollmentTable({ enrollments, onDetail }: { enrollments: Enrollment[]; onDetail: (item: Enrollment) => void }) { return <div className="table-scroll" tabIndex={0} role="region" aria-label="수강 현황 목록"><table className="admin-table enrollment-table"><thead><tr><th>직원</th><th>교육과정</th><th>배정 방식</th><th>상태</th><th>배정일</th><th>마감일</th><th>관리</th></tr></thead><tbody>{enrollments.map(item => <tr key={item.enrollmentId}><th>{item.memberName}<small className="table-secondary">회원 #{item.memberId}</small></th><td>{item.courseTitle}</td><td><span className="source-badge">{assignmentSources[item.assignmentSource]}</span>{item.assignmentRuleId && <small className="table-secondary">규칙 #{item.assignmentRuleId}</small>}</td><td><EnrollmentStatusBadge status={item.status} /></td><td>{item.assignedAt.replace('T', ' ').slice(0, 16)}</td><td>{item.dueDate}</td><td><button className="admin-button" onClick={() => onDetail(item)}>상세 보기</button></td></tr>)}</tbody></table></div> }

export function EnrollmentsPage() {
  const [params, setParams] = useSearchParams()
  const pageValue = Number(params.get('page') ?? 0)
  const page = Number.isSafeInteger(pageValue) && pageValue >= 0 ? pageValue : 0
  const courseId = Number(params.get('courseId'))
  const validId = Number.isSafeInteger(courseId) && courseId > 0
  const courses = useRemote(useCallback((signal: AbortSignal) => courseApi.list({ page, size: 100 }, signal), [page]))
  const selection = useRemote(useCallback((signal: AbortSignal) => validId ? courseApi.get(courseId, signal) : Promise.resolve(null), [courseId, validId]))
  const selected = selection.data
  function changePage(value: number) { setParams(current => { current.set('page', String(value)); return current }) }
  return <div className="management-page"><PageHeader title="수강 현황" description="교육과정을 선택해 배정된 직원과 학습 상태를 조회합니다." />
    {courses.loading ? <LoadingState /> : courses.error ? <ErrorState message={courses.error} retry={courses.reload} /> : !courses.data?.totalElements ? <EmptyState message="등록된 교육과정이 없습니다." /> : <>
      <label className="admin-field course-selector">교육과정<select value={validId ? courseId : ''} onChange={event => setParams(current => { if (event.target.value) current.set('courseId', event.target.value); else current.delete('courseId'); return current })}><option value="">조회할 교육과정 선택</option>{selected && !courses.data.content.some(course => course.id === selected.id) && <option value={selected.id}>{selected.title}</option>}{courses.data.content.map(course => <option key={course.id} value={course.id}>{course.title} · {course.status}</option>)}</select><small>선택한 교육과정의 수강 현황을 표시합니다.</small></label>
      {courses.data.totalPages > 1 && <div className="row-actions"><button className="admin-button" disabled={page === 0} onClick={() => changePage(page - 1)}>이전 교육과정</button><span>{page + 1} / {courses.data.totalPages}</span><button className="admin-button" disabled={page + 1 >= courses.data.totalPages} onClick={() => changePage(page + 1)}>다음 교육과정</button></div>}
    </>}
    {validId && selection.loading ? <LoadingState /> : selection.error ? <ErrorState message={selection.error} retry={selection.reload} /> : selected ? <CourseEnrollments key={selected.id} course={selected} /> : !!courses.data?.totalElements && <EmptyState message="수강 현황을 조회할 교육과정을 선택해 주세요." />}
  </div>
}

function CourseEnrollments({ course }: { course: Course }) {
  const [filters, setFilters] = useState<EnrollmentSearch>({ page: 0, size: 20 }); const [assigning, setAssigning] = useState(false); const [detail, setDetail] = useState<Enrollment | null>(null)
  const loader = useCallback((signal: AbortSignal) => enrollmentApi.forCourse(course.id, filters, signal), [course.id, filters]); const query = useRemote(loader); const data = query.data
  function filter(change: Partial<EnrollmentSearch>) { setFilters(current => ({ ...current, ...change, page: 0 })) }
  return <section className="surface enrollment-section"><div className="enrollment-heading"><div><h2>{course.title}</h2><p className="admin-hint">상태 변경과 삭제는 제공하지 않습니다.</p></div><button className="admin-button primary-button" disabled={course.status !== 'OPEN'} title={course.status !== 'OPEN' ? '운영 중인 과정만 수동 배정할 수 있습니다.' : undefined} onClick={() => setAssigning(true)}>+ 직원 수동 배정</button></div>
    <div className="admin-toolbar enrollment-toolbar"><label className="admin-field">수강 상태<select value={filters.status ?? ''} onChange={event => filter({ status: event.target.value as EnrollmentStatus || undefined })}><option value="">전체</option>{Object.entries(enrollmentStatuses).map(([value, item]) => <option key={value} value={value}>{item.label}</option>)}</select></label></div>
    {query.loading ? <LoadingState /> : query.error ? <ErrorState message={query.error} retry={query.reload} /> : !data?.content.length ? <EmptyState message="조건에 맞는 수강 내역이 없습니다." /> : <EnrollmentTable enrollments={data.content} onDetail={setDetail} />}
    {data && <div className="admin-pagination"><span>전체 {data.totalElements}건 · {data.totalPages ? data.page + 1 : 0} / {data.totalPages} 페이지</span><div className="row-actions"><label>표시 개수 <select value={filters.size} onChange={event => filter({ size: Number(event.target.value) })}>{[10, 20, 50, 100].map(size => <option key={size}>{size}</option>)}</select></label><button className="admin-button" disabled={data.page <= 0} onClick={() => setFilters(current => ({ ...current, page: current.page - 1 }))}>이전</button><button className="admin-button" disabled={data.page + 1 >= data.totalPages} onClick={() => setFilters(current => ({ ...current, page: current.page + 1 }))}>다음</button></div></div>}
    {assigning && <ManualEnrollmentModal course={course} onClose={() => setAssigning(false)} onSaved={() => { setAssigning(false); query.reload() }} />}{detail && <EnrollmentDetail item={detail} onClose={() => setDetail(null)} />}
  </section>
}

function ManualEnrollmentModal({ course, onClose, onSaved }: { course: Course; onClose: () => void; onSaved: () => void }) {
  const action = useAction(); const [validation, setValidation] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const memberId = Number(new FormData(event.currentTarget).get('memberId')); setValidation(''); let assigned = false; const saved = await action.run(async () => { const member = await memberApi.get(memberId); if (!eligibleForManualAssignment(member.status)) { setValidation('퇴사한 직원은 수동 배정할 수 없습니다.'); return } await enrollmentApi.assign(course.id, memberId); assigned = true }, '직원을 수동 배정했습니다.'); if (saved && assigned) onSaved() }
  return <Modal title="직원 수동 배정" busy={action.pending} onClose={onClose}><form className="admin-form" onSubmit={event => void submit(event)}><p><strong>{course.title}</strong></p><label className="admin-field">회원 ID<input name="memberId" type="number" min="1" required /><small>재직 또는 휴직 회원을 지정할 수 있습니다. 퇴사자는 제외됩니다.</small></label><Feedback error={validation || action.error} /><div className="admin-actions"><button className="admin-button" type="button" disabled={action.pending} onClick={onClose}>취소</button><SubmitButton pending={action.pending} label="수동 배정" /></div></form></Modal>
}
function EnrollmentDetail({ item, onClose }: { item: Enrollment; onClose: () => void }) { return <Modal title="수강 상세" onClose={onClose}><dl className="enrollment-detail"><div><dt>직원</dt><dd>{item.memberName} (#{item.memberId})</dd></div><div><dt>교육과정</dt><dd>{item.courseTitle}</dd></div><div><dt>상태</dt><dd><EnrollmentStatusBadge status={item.status} /></dd></div><div><dt>배정 방식</dt><dd>{assignmentSources[item.assignmentSource]}{item.assignmentRuleId ? ` · 규칙 #${item.assignmentRuleId}` : ''}</dd></div><div><dt>배정일</dt><dd>{item.assignedAt}</dd></div><div><dt>시작일</dt><dd>{item.startedAt ?? '미시작'}</dd></div><div><dt>완료일</dt><dd>{item.completedAt ?? '미완료'}</dd></div><div><dt>마감일</dt><dd>{item.dueDate}</dd></div></dl></Modal> }
