import { useCallback, useState, type FormEvent } from 'react'
import type { EmployeeAssignment, SubmissionType } from '../../api/courseworkTypes.ts'
import { courseworkApi } from '../../api/courseworkApi.ts'
import { Feedback, SubmitButton } from '../../components/admin/AdminUI.tsx'
import { useRemote } from '../../hooks/useRemote.ts'
import { useAction } from '../../hooks/useAction.ts'
import { canSubmitAssignment, submissionNotice, submissionValidation } from './courseworkUtils.ts'
import { AssignmentStatusBadge, CourseworkState, SubmissionInfo } from './CourseworkUI.tsx'

export function EmployeeAssignmentCard({ item, enrollmentId, onSaved }: { item: EmployeeAssignment; enrollmentId: number; onSaved: () => void }) {
  const action = useAction()
  const [type, setType] = useState<SubmissionType>(item.submission?.submissionType ?? 'TEXT')
  const [content, setContent] = useState(item.submission?.content ?? '')
  const [validation, setValidation] = useState('')
  const allowed = canSubmitAssignment(item)
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!allowed) return
    const request = { submissionType: type, content: content.trim() }
    const error = submissionValidation(request)
    setValidation(error)
    if (error) return
    const saved = await action.run(async () => { await courseworkApi.submit(enrollmentId, item.assignment.assignmentId, request) }, '과제를 제출했습니다.')
    if (saved) onSaved()
  }
  return <article className="coursework-card coursework-stack">
    <div className="section-heading"><div className="coursework-copy"><h3>{item.assignment.title}</h3><span className={`status-badge tone-${item.assignment.required ? 'primary' : 'neutral'}`}>{item.assignment.required ? '필수' : '선택'}</span></div><AssignmentStatusBadge status={item.status} /></div>
    <p className="coursework-copy">{item.assignment.description || '등록된 설명이 없습니다.'}</p>
    <p className="admin-hint">제출 마감 {item.effectiveDueDate} (서울, 당일까지) · 통과 기준 {item.assignment.passingScore}점</p>
    {item.submission ? <SubmissionInfo submission={item.submission} /> : <p className="admin-hint">제출 횟수 0회 · 아직 제출하지 않았습니다.</p>}
    <p className="admin-hint">{submissionNotice(item)}</p>
    <Feedback error={validation || action.error} success={action.success} />
    {allowed && <form className="admin-form" onSubmit={event => void submit(event)}>
      <fieldset disabled={action.pending}>
        <label className="admin-field">제출 형식<select value={type} onChange={event => setType(event.target.value as SubmissionType)}><option value="TEXT">텍스트</option><option value="URL">URL</option></select></label>
        {type === 'TEXT' ? <label className="admin-field">제출 내용<textarea value={content} onChange={event => setContent(event.target.value)} required maxLength={10000} rows={5} /></label> : <label className="admin-field">제출 URL<input type="url" value={content} onChange={event => setContent(event.target.value)} required maxLength={2048} placeholder="https://" /></label>}
      </fieldset>
      <div className="admin-actions"><SubmitButton pending={action.pending} label={item.submission ? '재제출' : '과제 제출'} /></div>
    </form>}
  </article>
}
export function EmployeeAssignmentsSection({ enrollmentId, onRefresh }: { enrollmentId: number; onRefresh: () => void }) {
  const query = useRemote(useCallback((signal: AbortSignal) => courseworkApi.mine(enrollmentId, signal), [enrollmentId]))
  return <section className="surface detail-section coursework-stack" aria-label="나의 과제">
    <div className="section-heading"><div><h2>과제</h2><p className="admin-hint">모든 필수 과제를 통과해야 수료할 수 있습니다.</p></div><button className="admin-button" disabled={query.loading} onClick={onRefresh}>과제 / 수강 상태 새로고침</button></div>
    <CourseworkState loading={query.loading} error={query.error} empty={!query.data?.length} emptyMessage="등록된 과제가 없습니다." retry={query.reload}>
      {[...(query.data ?? [])].sort((a, b) => a.assignment.sortOrder - b.assignment.sortOrder).map(item => <EmployeeAssignmentCard key={`${item.assignment.assignmentId}:${item.submission?.version ?? 'new'}`} item={item} enrollmentId={enrollmentId} onSaved={onRefresh} />)}
    </CourseworkState>
  </section>
}
