import { useCallback, useRef, useState, useSyncExternalStore } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { employeeExamApi, loadEmployeeAttempt } from '../../api/employeeExamApi.ts'
import { employeeLearningApi } from '../../api/employeeLearningApi.ts'
import type { EmployeeExamAttempt, EmployeeExamPaper, EmployeeExamQuestion } from '../../api/employeeExamTypes.ts'
import type { MyLearningDetail } from '../../api/learningTypes.ts'
import { ErrorState, Feedback, LoadingState } from '../../components/admin/AdminUI.tsx'
import { ConfirmDialog } from '../../components/admin/Modal.tsx'
import { useRemote } from '../../hooks/useRemote.ts'
import { createAnswerSession } from './answerSession.ts'
import { employeeQuestionLabels, examErrorMessage, selectChoice, submissionDescription } from './employeeExamUtils.ts'
import { isTerminal } from './learningUtils.ts'

export function ExamQuestion({ question, selected, disabled, onChange }: {
  question: EmployeeExamQuestion; selected: number[]; disabled: boolean; onChange: (ids: number[]) => void
}) {
  return <fieldset className="employee-exam-question" disabled={disabled}>
    <legend>{question.questionText}</legend><p>{employeeQuestionLabels[question.questionType]} · 배점 {question.score}점</p>
    <div className="employee-exam-choices">{[...question.choices].sort((a, b) => a.sortOrder - b.sortOrder).map(choice => <label key={choice.choiceId}>
      <input type={question.questionType === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'} name={`question-${question.questionId}`} value={choice.choiceId} checked={selected.includes(choice.choiceId)} onChange={() => onChange(selectChoice(question.questionType, selected, choice.choiceId))} />
      <span>{choice.choiceText}</span>
    </label>)}</div>
  </fieldset>
}
export function EmployeeExamPage() {
  const params = useParams()
  const enrollmentId = Number(params.enrollmentId), attemptId = Number(params.attemptId)
  if (![enrollmentId, attemptId].every(id => Number.isSafeInteger(id) && id > 0)) return <p role="alert">올바른 시험 주소가 아닙니다.</p>
  return <AttemptLoader key={`${enrollmentId}-${attemptId}`} enrollmentId={enrollmentId} attemptId={attemptId} />
}
function AttemptLoader({ enrollmentId, attemptId }: { enrollmentId: number; attemptId: number }) {
  const query = useRemote(useCallback((signal: AbortSignal) => loadEmployeeAttempt(enrollmentId, attemptId, signal), [enrollmentId, attemptId]))
  if (query.loading) return <LoadingState />
  if (query.error) return <ErrorState message={query.error} retry={query.reload} />
  const { detail, attempt, paper } = query.data!
  const back = <Link className="admin-button" to={`/employee/learning/${enrollmentId}`}>교육으로 돌아가기</Link>
  if (!attempt) return <div className="learning-page">{back}<p role="alert">이 교육에 해당하는 시험 응시를 찾을 수 없습니다.</p></div>
  if (attempt.submittedAt) return <Navigate to={`/employee/learning/${enrollmentId}/exam/${attemptId}/result`} replace />
  if (isTerminal(detail.status)) return <div className="learning-page">{back}<p>종료된 교육에서는 시험을 진행할 수 없습니다.</p></div>
  if (!paper?.questions.length || !detail.exam) return <div className="learning-page">{back}<p>현재 시험 구성이 완료되지 않았습니다.</p></div>
  return <AttemptEditor detail={detail} attempt={attempt} paper={paper} onRefresh={query.reload} />
}
function AttemptEditor({ detail, attempt, paper, onRefresh }: { detail: MyLearningDetail; attempt: EmployeeExamAttempt; paper: EmployeeExamPaper; onRefresh: () => void }) {
  const [session] = useState(() => createAnswerSession(paper, {
    save: (questionId, ids) => employeeExamApi.saveAnswer(attempt.attemptId, questionId, ids),
    submit: () => employeeExamApi.submit(attempt.attemptId),
  }))
  const snapshot = useSyncExternalStore(session.subscribe, session.getSnapshot, session.getSnapshot)
  const questions = [...paper.questions].sort((a, b) => a.sortOrder - b.sortOrder)
  const [index, setIndex] = useState(0)
  const [confirm, setConfirm] = useState(false)
  const [error, setError] = useState('')
  const [recovering, setRecovering] = useState(false)
  const busy = useRef(false)
  const navigate = useNavigate()
  const answered = questions.filter(q => snapshot.answers[q.questionId]?.length).length
  const disabled = snapshot.submitting || snapshot.submitted || snapshot.conflict || recovering
  async function submit() {
    if (busy.current) return
    busy.current = true; setError('')
    try {
      await session.submit()
      navigate(`/employee/learning/${detail.enrollmentId}/exam/${attempt.attemptId}/result`, { replace: true })
    } catch (cause) {
      setError(snapshot.failed.length ? '저장 실패한 답안을 다시 저장한 뒤 제출해 주세요.' : examErrorMessage(cause))
      // 제출 성공 응답 유실 또는 다른 탭 제출도 실제 이력과 수강 상태로 복구한다.
      setRecovering(true)
      try {
        const [history, fresh] = await Promise.all([employeeExamApi.history(detail.enrollmentId), employeeLearningApi.detail(detail.enrollmentId)])
        if (history.find(item => item.attemptId === attempt.attemptId)?.submittedAt) {
          navigate(`/employee/learning/${detail.enrollmentId}/exam/${attempt.attemptId}/result`, { replace: true })
        } else if (isTerminal(fresh.status)) onRefresh()
      } catch { setError('최신 상태를 확인하지 못했습니다. 연결을 확인한 뒤 다시 시도해 주세요.') }
      finally { setRecovering(false) }
    } finally { busy.current = false }
  }
  return <div className="learning-page employee-exam-page">
    <Link className="admin-button exam-back" to={`/employee/learning/${detail.enrollmentId}`}>교육으로 돌아가기</Link>
    <section className="surface employee-exam-section"><h2>{paper.title}</h2><p>응시 {attempt.attemptNumber} / {attempt.maxAttempts} · 합격 기준 {detail.exam!.passingScore}점</p><p>시험을 나가도 저장된 답안은 유지됩니다. 저장 중에는 새로고침을 기다려 주세요.</p></section>
    <section className="surface employee-exam-section">
      <nav className="exam-question-nav" aria-label="시험 문항">{questions.map((q, i) => <button className={`admin-button${index === i ? ' primary-button' : ''}`} key={q.questionId} type="button" disabled={disabled} aria-current={index === i ? 'step' : undefined} onClick={() => setIndex(i)}>{i + 1} · {snapshot.answers[q.questionId]?.length ? '응답 완료' : '미응답'}</button>)}</nav>
      <p>문항 {index + 1} / {questions.length}</p>
      <ExamQuestion question={questions[index]} selected={snapshot.answers[questions[index].questionId] ?? []} disabled={disabled} onChange={ids => session.change(questions[index].questionId, ids)} />
      <p role="status" aria-live="polite">{snapshot.pending ? '답안 저장 중…' : snapshot.failed.length ? '저장 실패' : '저장됨'}</p>
      <Feedback error={snapshot.error} />
      {snapshot.failed.length > 0 && !snapshot.conflict && <button className="admin-button" disabled={disabled || snapshot.pending > 0} onClick={session.retry}>답안 저장 다시 시도</button>}
      {snapshot.conflict && <button className="admin-button" onClick={onRefresh}>최신 상태 확인</button>}
      <div className="exam-navigation"><button className="admin-button" disabled={disabled || index === 0} onClick={() => setIndex(i => i - 1)}>이전</button>
        {index < questions.length - 1 ? <button className="admin-button primary-button" disabled={disabled} onClick={() => setIndex(i => i + 1)}>다음</button> : <button className="admin-button primary-button" disabled={disabled || snapshot.failed.length > 0} onClick={() => { setError(''); setConfirm(true) }}>시험 제출</button>}
      </div>
    </section>
    {confirm && <ConfirmDialog title="시험을 제출하시겠습니까?" description={submissionDescription(questions.length, answered)} label="시험 제출" tone="primary" pending={snapshot.submitting || recovering} error={error || snapshot.error} onConfirm={() => void submit()} onClose={() => setConfirm(false)} />}
  </div>
}
