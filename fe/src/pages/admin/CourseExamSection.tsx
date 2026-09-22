import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import { examApi } from '../../api/examApi.ts'
import { EmptyState, ErrorState, LoadingState } from '../../components/admin/AdminUI.tsx'
import { useRemote } from '../../hooks/useRemote.ts'

export function CourseExamSection({ courseId }: { courseId: number }) {
  const loader = useCallback((signal: AbortSignal) => examApi.getOptional(courseId, signal), [courseId])
  const query = useRemote(loader)
  return <section className="surface detail-section course-exam-section" aria-labelledby="course-exam-heading">
    <div className="section-heading"><div><h2 id="course-exam-heading">시험</h2><p className="admin-hint">시험은 선택 사항이며 과정당 하나만 구성할 수 있습니다.</p></div>{query.data && <Link className="admin-button primary-button" to={`/admin/courses/${courseId}/exam`}>시험 관리</Link>}</div>
    {query.loading ? <LoadingState /> : query.error ? <ErrorState message={query.error} retry={query.reload} /> : query.data ? <div className="exam-inline-summary"><strong>{query.data.title}</strong><span>합격 {query.data.passingScore}점</span><span>최대 {query.data.maxAttempts}회</span><span>문항 {query.data.questionCount}개</span><span>총 배점 {query.data.totalQuestionScore}점</span></div> : <EmptyState message="등록된 시험이 없습니다. 시험이 필요한 교육과정이라면 추가할 수 있습니다."><Link className="admin-button primary-button" to={`/admin/courses/${courseId}/exam`}>시험 만들기</Link></EmptyState>}
  </section>
}
