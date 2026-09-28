import { useCallback, useState, type FormEvent } from 'react'
import { courseworkApi } from '../../api/courseworkApi.ts'
import type { Assignment, Submission, SubmissionSearch } from '../../api/courseworkTypes.ts'
import { useAuth } from '../../auth/AuthContext.ts'
import { employeeLearningApi } from '../../api/employeeLearningApi.ts'
import type { EnrollmentStatus } from '../../api/assignmentTypes.ts'
import { Feedback, SubmitButton } from '../../components/admin/AdminUI.tsx'
import { Modal } from '../../components/admin/Modal.tsx'
import { useAction } from '../../hooks/useAction.ts'
import { useRemote } from '../../hooks/useRemote.ts'
import { LearningStatusBadge } from '../employee/LearningPage.tsx'
import { assignmentStatuses, scoreText, submissionTime } from './courseworkUtils.ts'
import { AssignmentStatusBadge, CourseworkState, SubmissionInfo } from './CourseworkUI.tsx'

export function SubmissionTable({ rows, assignments, onSelect }: { rows: Submission[]; assignments: Assignment[]; onSelect: (row: Submission) => void }) {
  const titles = new Map(assignments.map(a => [a.assignmentId, a.title]))
  return <div className="table-scroll" tabIndex={0} role="region" aria-label="과제 제출물 목록"><table className="admin-table"><thead><tr><th scope="col">직원</th><th scope="col">과제</th><th scope="col">상태</th><th scope="col">제출 횟수</th><th scope="col">제출 시각 (서울)</th><th scope="col">점수</th><th scope="col">관리</th></tr></thead><tbody>{rows.map(row => <tr key={row.submissionId}><th scope="row">{row.memberName}<small className="table-secondary">직원 #{row.memberId}</small></th><td>{titles.get(row.assignmentId) ?? `과제 #${row.assignmentId}`}</td><td><AssignmentStatusBadge status={row.status} /></td><td>{row.submissionCount}회</td><td>{submissionTime(row.submittedAt)}</td><td>{scoreText(row.score)}</td><td><button className="admin-button" onClick={() => onSelect(row)}>{row.gradedAt === null && row.status === 'PENDING_GRADING' ? '조회 / 채점' : '결과 보기'}</button></td></tr>)}</tbody></table></div>
}
export function GradeForm({ pending, onSubmit }: { pending: boolean; onSubmit: (score: number, feedback: string | null) => Promise<void> }) {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    void onSubmit(Number(form.get('score')), String(form.get('feedback') ?? '').trim() || null)
  }
  return <form className="admin-form detail-divider" onSubmit={submit}>
    <p className="admin-hint">입력한 점수로 서버가 합격 여부를 판정합니다. 확정 후에는 채점을 수정할 수 없습니다.</p>
    <fieldset disabled={pending}><label className="admin-field">점수<input name="score" type="number" required min="0" max="100" step="0.01" /></label><label className="admin-field">피드백<textarea name="feedback" rows={4} maxLength={10000} /></label></fieldset>
    <div className="admin-actions"><SubmitButton pending={pending} label="채점 확정" /></div>
  </form>
}
function GradingModal({ row, title, onClose, onGraded }: { row: Submission; title: string; onClose: () => void; onGraded: () => void }) {
  const { user } = useAuth()
  const query = useRemote(useCallback((signal: AbortSignal) => courseworkApi.submission(row.submissionId, signal), [row.submissionId]))
  const action = useAction()
  const [graded, setGraded] = useState<Submission | null>(null)
  const [enrollmentStatus, setEnrollmentStatus] = useState<EnrollmentStatus | null>(null)
  const [statusError, setStatusError] = useState('')
  const detail = graded ?? query.data
  async function refreshEnrollment(enrollmentId: number) {
    // 진행 조회 API는 ADMIN/본인 EMPLOYEE 전용. 강사에게 없는 권한을 요청하지 않는다.
    if (!user?.roles.includes('ADMIN')) return
    try { setEnrollmentStatus((await employeeLearningApi.detail(enrollmentId)).status); setStatusError('') }
    catch { setStatusError('채점은 저장되었지만 수강 상태를 불러오지 못했습니다.') }
  }
  return <Modal title={`${row.memberName} · ${title}`} onClose={onClose} busy={action.pending}>
    <Feedback error={action.error} success={action.success} />
    <CourseworkState loading={!graded && query.loading} error={!graded ? query.error : ''} empty={false} emptyMessage="" retry={query.reload}>
      {detail && <><SubmissionInfo submission={detail} />
        {detail.gradedAt === null && detail.status === 'PENDING_GRADING' ? <GradeForm pending={action.pending} onSubmit={async (score, feedback) => {
          await action.run(async () => {
            const result = await courseworkApi.grade(detail.submissionId, { score, feedback })
            setGraded(result)
            onGraded()
            await refreshEnrollment(result.enrollmentId)
          }, '채점을 저장했습니다.')
        }} /> : <p className="admin-hint">확정된 채점은 수정할 수 없습니다.</p>}
      </>}
    </CourseworkState>
    {enrollmentStatus && <p className="status-summary">수강 상태 <LearningStatusBadge status={enrollmentStatus} /></p>}
    {statusError && <div><p className="admin-error" role="alert">{statusError}</p><button className="admin-button" onClick={() => void refreshEnrollment(row.enrollmentId)}>수강 상태 다시 조회</button></div>}
  </Modal>
}
export function SubmissionsSection({ courseId, assignments }: { courseId: number; assignments: Assignment[] }) {
  const [filters, setFilters] = useState<SubmissionSearch>({ page: 0, size: 20 })
  const [selected, setSelected] = useState<Submission | null>(null)
  const query = useRemote(useCallback((signal: AbortSignal) => courseworkApi.submissions(courseId, filters, signal), [courseId, filters]))
  const page = query.data
  return <section className="surface detail-section coursework-stack" aria-label="제출물 관리">
    <div className="section-heading"><div><h2>제출물 / 채점</h2><p className="admin-hint">제출된 과제를 조회합니다. 미제출 직원은 이 목록에 포함되지 않습니다.</p></div><button className="admin-button" disabled={query.loading} onClick={query.reload}>제출물 새로고침</button></div>
    <div className="admin-toolbar">
      <label className="admin-field">과제<select value={filters.assignmentId ?? ''} onChange={event => setFilters({ ...filters, page: 0, assignmentId: event.target.value ? Number(event.target.value) : undefined })}><option value="">전체 과제</option>{assignments.map(a => <option key={a.assignmentId} value={a.assignmentId}>{a.title}</option>)}</select></label>
      <label className="admin-field">제출 상태<select value={filters.status ?? ''} onChange={event => setFilters({ ...filters, page: 0, status: event.target.value as SubmissionSearch['status'] || undefined })}><option value="">전체 상태</option>{(['PENDING_GRADING', 'PASSED', 'FAILED'] as const).map(status => <option key={status} value={status}>{assignmentStatuses[status].label}</option>)}</select></label>
    </div>
    <CourseworkState loading={query.loading} error={query.error} empty={!page?.content.length} emptyMessage="조회된 제출물이 없습니다." retry={query.reload}>
      {page && <SubmissionTable rows={page.content} assignments={assignments} onSelect={setSelected} />}
    </CourseworkState>
    {page && <div className="admin-pagination"><span>전체 {page.totalElements}건 · {page.totalPages ? page.page + 1 : 0} / {page.totalPages} 페이지</span><div className="row-actions"><button className="admin-button" disabled={page.page <= 0} onClick={() => setFilters({ ...filters, page: filters.page - 1 })}>이전</button><button className="admin-button" disabled={page.page + 1 >= page.totalPages} onClick={() => setFilters({ ...filters, page: filters.page + 1 })}>다음</button></div></div>}
    {selected && <GradingModal key={selected.submissionId} row={selected} title={assignments.find(a => a.assignmentId === selected.assignmentId)?.title ?? `과제 #${selected.assignmentId}`} onClose={() => setSelected(null)} onGraded={query.reload} />}
  </section>
}
