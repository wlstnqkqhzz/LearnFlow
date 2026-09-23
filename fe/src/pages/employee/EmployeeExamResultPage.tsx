import { useCallback } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { employeeExamApi } from '../../api/employeeExamApi.ts'
import { employeeLearningApi } from '../../api/employeeLearningApi.ts'
import type { EmployeeExamAttempt } from '../../api/employeeExamTypes.ts'
import type { MyLearningDetail } from '../../api/learningTypes.ts'
import { ErrorState, LoadingState } from '../../components/admin/AdminUI.tsx'
import { useRemote } from '../../hooks/useRemote.ts'
import { EmployeeExamSection } from './EmployeeExamSection.tsx'
import { LearningStatusBadge } from './LearningPage.tsx'
import { scoreLabel } from './employeeExamUtils.ts'

export function ExamResultView({ detail, result }: { detail: MyLearningDetail; result: EmployeeExamAttempt }) {
  return <section className="surface employee-exam-section employee-exam-result">
    <h2>{detail.exam?.title ?? '시험'} 결과</h2><strong className="exam-result-score">{scoreLabel(result.score)}</strong>
    <span className={`status-badge tone-${result.passed ? 'success' : 'danger'}`}>{result.passed ? '합격' : '불합격'}</span>
    <p>응시 {result.attemptNumber} / {result.maxAttempts} · 남은 응시 {result.remainingAttempts}회{detail.exam && ` · 합격 기준 ${scoreLabel(detail.exam.passingScore)}`}</p>
    <div>현재 교육 상태 <LearningStatusBadge status={detail.status} /></div>
    {detail.status === 'COMPLETED' ? <p>교육과정을 수료했습니다.</p> : detail.status === 'FAILED' ? <p>시험 응시 기회를 모두 사용했습니다. 교육이 실패 상태로 종료되었습니다.</p> : detail.status === 'EXPIRED' ? <p>교육 기간이 만료되었습니다.</p> : result.passed ? <p>시험을 통과했습니다. 남은 필수 학습을 완료하면 교육과정을 수료할 수 있습니다.</p> : <p>합격 기준에 도달하지 못했습니다.</p>}
  </section>
}
export function EmployeeExamResultPage() {
  const params = useParams()
  const enrollmentId = Number(params.enrollmentId), attemptId = Number(params.attemptId)
  if (![enrollmentId, attemptId].every(id => Number.isSafeInteger(id) && id > 0)) return <p role="alert">올바른 시험 결과 주소가 아닙니다.</p>
  return <ResultLoader key={`${enrollmentId}-${attemptId}`} enrollmentId={enrollmentId} attemptId={attemptId} />
}
function ResultLoader({ enrollmentId, attemptId }: { enrollmentId: number; attemptId: number }) {
  const query = useRemote(useCallback(async (signal: AbortSignal) => {
    const [detail, history] = await Promise.all([employeeLearningApi.detail(enrollmentId, signal), employeeExamApi.history(enrollmentId, signal)])
    const attempt = history.find(item => item.attemptId === attemptId)
    const result = attempt?.submittedAt ? await employeeExamApi.result(attemptId, signal) : null
    return { detail, attempt, result }
  }, [enrollmentId, attemptId]))
  if (query.loading) return <LoadingState />
  if (query.error) return <ErrorState message={query.error} retry={query.reload} />
  const { detail, attempt, result } = query.data!
  if (attempt && !attempt.submittedAt) return <Navigate to={`/employee/learning/${enrollmentId}/exam/${attemptId}`} replace />
  return <div className="learning-page employee-exam-page"><Link className="admin-button exam-back" to={`/employee/learning/${enrollmentId}`}>교육으로 돌아가기</Link>
    {!result ? <p role="alert">이 교육에 해당하는 시험 결과를 찾을 수 없습니다.</p> : <><ExamResultView detail={detail} result={result} /><EmployeeExamSection detail={detail} onRefresh={query.reload} /></>}
  </div>
}
