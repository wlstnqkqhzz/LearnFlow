import { useCallback, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import type { EmployeeExamAttempt } from '../../api/employeeExamTypes.ts'
import type { MyLearningDetail } from '../../api/learningTypes.ts'
import { employeeExamApi } from '../../api/employeeExamApi.ts'
import { employeeLearningApi } from '../../api/employeeLearningApi.ts'
import { ErrorState, LoadingState } from '../../components/admin/AdminUI.tsx'
import { ConfirmDialog } from '../../components/admin/Modal.tsx'
import { useRemote } from '../../hooks/useRemote.ts'
import { examAction, examErrorMessage, scoreLabel } from './employeeExamUtils.ts'

export function ExamOverview({ detail, history, pending, onStart }: {
  detail: MyLearningDetail; history: EmployeeExamAttempt[]; pending: boolean; onStart: () => void
}) {
  const exam = detail.exam
  if (!exam) return <p>이 교육과정에는 시험이 없습니다.</p>
  const state = examAction(detail.status, history, exam.maxAttempts)
  const base = `/employee/learning/${detail.enrollmentId}/exam`
  return <>
    <h2>{exam.title}</h2>
    <p>합격 기준 {scoreLabel(exam.passingScore)} · 최대 {exam.maxAttempts}회 · 시작한 응시 {history.length}회 · 남은 응시 {state.remaining}회</p>
    {detail.status === 'EXPIRED' && <p>교육 기간이 만료되어 시험에 응시할 수 없습니다.</p>}
    {detail.status === 'FAILED' && <p>교육 상태: 실패. 추가 응시는 할 수 없습니다.</p>}
    {detail.status === 'COMPLETED' && <p>교육과정을 수료했습니다.</p>}
    {state.passed && <p>시험에 합격했습니다.</p>}
    {!state.passed && !state.open && state.remaining === 0 && <p>시험 응시 기회를 모두 사용했습니다.</p>}
    <div className="row-actions">
      {state.canStart && <button className="admin-button primary-button" disabled={pending} onClick={onStart}>{pending ? '시작 중…' : history.length ? '다시 응시하기' : '시험 응시하기'}</button>}
      {state.canContinue && <Link className="admin-button primary-button" to={`${base}/${state.open!.attemptId}`}>시험 계속하기</Link>}
      {state.passed && <Link className="admin-button" to={`${base}/${state.passed.attemptId}/result`}>시험 결과 보기</Link>}
    </div>
    {history.length > 0 && <div className="employee-exam-history"><h3>응시 이력</h3><ul>{[...history].sort((a, b) => b.attemptNumber - a.attemptNumber).map(attempt => <li key={attempt.attemptId}>
      <span>{attempt.attemptNumber}회차</span><span>{attempt.submittedAt ? `${scoreLabel(attempt.score)} · ${attempt.passed ? '합격' : '불합격'}` : '진행 중 (미제출)'}</span>
      {attempt.submittedAt && <Link to={`${base}/${attempt.attemptId}/result`}>{attempt.attemptNumber}회차 결과 보기</Link>}
    </li>)}</ul></div>}
  </>
}

export function EmployeeExamSection({ detail, onRefresh }: { detail: MyLearningDetail; onRefresh: () => void }) {
  if (!detail.exam) return <section className="surface employee-exam-section"><h2>시험</h2><p>이 교육과정에는 시험이 없습니다.</p></section>
  return <ExamSectionWithHistory detail={detail} onRefresh={onRefresh} />
}
function ExamSectionWithHistory({ detail, onRefresh }: { detail: MyLearningDetail; onRefresh: () => void }) {
  const history = useRemote(useCallback((signal: AbortSignal) => employeeExamApi.history(detail.enrollmentId, signal), [detail.enrollmentId]))
  const navigate = useNavigate()
  const busy = useRef(false)
  const [pending, setPending] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const [error, setError] = useState('')
  async function start() {
    if (busy.current) return
    busy.current = true; setPending(true); setError('')
    try {
      const attempt = await employeeExamApi.start(detail.enrollmentId)
      // 시작 API가 결정한 수강 상태를 재조회한 뒤 실제 attemptId로 진입한다.
      await employeeLearningApi.detail(detail.enrollmentId)
      navigate(`/employee/learning/${detail.enrollmentId}/exam/${attempt.attemptId}`)
    } catch (cause) {
      setError(examErrorMessage(cause))
      history.reload()
    } finally { busy.current = false; setPending(false) }
  }
  return <section className="surface employee-exam-section">
    {history.loading ? <LoadingState /> : history.error ? <ErrorState message={history.error} retry={history.reload} /> : <ExamOverview detail={detail} history={history.data!} pending={pending} onStart={() => setConfirm(true)} />}
    {confirm && <ConfirmDialog title="시험을 시작하시겠습니까?" description="새 시험을 시작하면 응시 1회로 기록됩니다. 미제출 응시가 있다면 해당 응시를 이어서 진행합니다." label="시험 시작" tone="primary" pending={pending} error={error} onConfirm={() => void start()} onClose={() => { setConfirm(false); if (error) onRefresh() }} />}
  </section>
}
