import type { AssignmentStatus, Submission } from '../../api/courseworkTypes.ts'
import { EmptyState, ErrorState, LoadingState } from '../../components/admin/AdminUI.tsx'
import type { ReactNode } from 'react'
import { assignmentStatuses, scoreText, submissionTime } from './courseworkUtils.ts'
import { safeResourceUrl } from '../employee/learningUtils.ts'

export function AssignmentStatusBadge({ status }: { status: AssignmentStatus }) {
  const item = assignmentStatuses[status]
  return <span className={`status-badge tone-${item.tone}`}>{item.label}</span>
}
export function CourseworkState({ loading, error, empty, emptyMessage, retry, children }: {
  loading: boolean; error: string; empty: boolean; emptyMessage: string; retry: () => void; children: ReactNode
}) {
  return loading ? <LoadingState /> : error ? <ErrorState message={error} retry={retry} /> : empty ? <EmptyState message={emptyMessage} /> : children
}
export function SubmissionContent({ submission }: { submission: Submission }) {
  const url = submission.submissionType === 'URL' ? safeResourceUrl(submission.content) : null
  return <div className="coursework-copy">{url ? <a href={url} target="_blank" rel="noopener noreferrer">{submission.content}</a> : <p>{submission.content}</p>}</div>
}
export function SubmissionInfo({ submission }: { submission: Submission }) {
  return <div className="coursework-stack">
    <div className="row-actions"><AssignmentStatusBadge status={submission.status} /><span>{submission.submissionCount}회 제출 · {submissionTime(submission.submittedAt)} (서울)</span></div>
    <SubmissionContent submission={submission} />
    <dl className="coursework-meta"><div><dt>점수</dt><dd>{scoreText(submission.score)}</dd></div><div><dt>채점일</dt><dd>{submissionTime(submission.gradedAt)}</dd></div>{submission.gradedByName && <div><dt>채점자</dt><dd>{submission.gradedByName}</dd></div>}</dl>
    <div className="coursework-copy"><strong>피드백</strong><p>{submission.feedback || '등록된 피드백이 없습니다.'}</p></div>
  </div>
}
