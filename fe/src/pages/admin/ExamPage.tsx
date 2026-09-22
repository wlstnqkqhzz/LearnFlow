import { useCallback, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { adminErrorCode, adminErrorMessage } from '../../api/adminError.ts'
import { courseApi } from '../../api/courseApi.ts'
import { examApi, questionApi } from '../../api/examApi.ts'
import type { AdminQuestion } from '../../api/examTypes.ts'
import { EmptyState, ErrorState, Feedback, LoadingState, PageHeader } from '../../components/admin/AdminUI.tsx'
import { ConfirmDialog } from '../../components/admin/Modal.tsx'
import { useRemote } from '../../hooks/useRemote.ts'
import { ExamModal } from './ExamModal.tsx'
import { QuestionModal } from './QuestionModal.tsx'
import { moveQuestion, questionTypes, totalQuestionScore } from './examUtils.ts'

export function ExamPage() {
  const { courseId } = useParams()
  const id = Number(courseId)
  if (!Number.isSafeInteger(id) || id <= 0) return <div className="management-page"><p role="alert">올바른 교육과정 주소가 아닙니다.</p><Link to="/admin/exams">시험 관리로</Link></div>
  return <ExamManagement key={id} courseId={id} />
}

export function QuestionList({ questions, orderedIds, locked, pending, onMove, onEdit, onDelete }: {
  questions: AdminQuestion[]
  orderedIds: number[]
  locked: boolean
  pending: boolean
  onMove: (index: number, direction: -1 | 1) => void
  onEdit: (question: AdminQuestion) => void
  onDelete: (question: AdminQuestion) => void
}) {
  const byId = new Map(questions.map(question => [question.questionId, question]))
  const ordered = orderedIds.map(id => byId.get(id)).filter((question): question is AdminQuestion => !!question)
  return <ol className="question-list">{ordered.map((question, index) => <li className="question-item" key={question.questionId}>
    <div className="question-main"><div className="question-title"><span className="question-number">{index + 1}</span><div><strong>{question.questionText}</strong><div className="question-meta"><span className="status-badge tone-neutral">{questionTypes[question.questionType]}</span><span>{question.score}점</span></div></div></div>
      <ul className="choice-preview">{question.choices.map(choice => <li key={choice.choiceId} className={choice.correct ? 'correct-choice' : ''}><span aria-hidden="true">{question.questionType === 'MULTIPLE_CHOICE' ? (choice.correct ? '☑' : '☐') : (choice.correct ? '●' : '○')}</span><span>{choice.choiceText === 'TRUE' ? 'O' : choice.choiceText === 'FALSE' ? 'X' : choice.choiceText}</span>{choice.correct && <span className="status-badge tone-success">정답</span>}</li>)}</ul>
    </div>
    <div className="question-actions"><span className="row-actions"><button className="admin-button icon-order-button" type="button" aria-label={`${question.questionText} 위로 이동`} disabled={locked || pending || index === 0} onClick={() => onMove(index, -1)}>↑</button><button className="admin-button icon-order-button" type="button" aria-label={`${question.questionText} 아래로 이동`} disabled={locked || pending || index === ordered.length - 1} onClick={() => onMove(index, 1)}>↓</button></span><span className="row-actions"><button className="admin-button" type="button" disabled={locked || pending} onClick={() => onEdit(question)}>수정</button><button className="admin-button danger-button" type="button" disabled={locked || pending} onClick={() => onDelete(question)}>삭제</button></span></div>
  </li>)}</ol>
}

function ExamManagement({ courseId }: { courseId: number }) {
  const courseQuery = useRemote(useCallback((signal: AbortSignal) => courseApi.get(courseId, signal), [courseId]))
  const examQuery = useRemote(useCallback((signal: AbortSignal) => examApi.getOptional(courseId, signal), [courseId]))
  const exam = examQuery.data
  const questionQuery = useRemote(useCallback((signal: AbortSignal) => exam ? questionApi.list(courseId, signal) : Promise.resolve([]), [courseId, exam]))
  const [examModal, setExamModal] = useState(false)
  const [questionModal, setQuestionModal] = useState<'create' | AdminQuestion | null>(null)
  const [deleting, setDeleting] = useState<AdminQuestion | null>(null)
  const [draftOrder, setDraftOrder] = useState<number[] | null>(null)
  const [locked, setLocked] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const original = useMemo(() => questionQuery.data?.map(question => question.questionId) ?? [], [questionQuery.data])
  const orderedIds = draftOrder ?? original
  const dirty = draftOrder !== null && orderedIds.join(',') !== original.join(',')
  const questions = questionQuery.data ?? []
  const nextOrder = Math.max(0, ...questions.map(question => question.sortOrder)) + 1

  async function mutate(action: () => Promise<void>, message: string) {
    if (pending) return false
    setPending(true); setError(''); setSuccess('')
    try { await action(); setSuccess(message); return true }
    catch (cause) {
      if (adminErrorCode(cause) === 'EXAM_CONFIGURATION_LOCKED' || adminErrorCode(cause) === 'EXAM_HISTORY_DELETE_CONFLICT') setLocked(true)
      setError(adminErrorMessage(cause)); return false
    } finally { setPending(false) }
  }
  function reload() { setDraftOrder(null); examQuery.reload(); questionQuery.reload() }
  async function saveOrder() {
    const saved = await mutate(async () => { await questionApi.reorder(courseId, orderedIds) }, '문항 순서를 저장했습니다.')
    if (saved) reload()
  }
  async function removeQuestion() {
    if (!deleting) return
    const removed = await mutate(async () => { await questionApi.delete(courseId, deleting.questionId) }, '문항을 삭제했습니다.')
    if (removed) { setDeleting(null); reload() }
  }
  async function validateConfiguration() {
    await mutate(async () => { await examApi.validate(courseId) }, '시험 구성이 유효합니다. 응시를 시작할 수 있습니다.')
  }
  function saved(message: string) { setExamModal(false); setQuestionModal(null); setSuccess(message); reload() }

  if (courseQuery.loading || examQuery.loading) return <div className="management-page"><LoadingState /></div>
  if (courseQuery.error) return <div className="management-page"><ErrorState message={courseQuery.error} retry={courseQuery.reload} /></div>
  if (examQuery.error) return <div className="management-page"><ErrorState message={examQuery.error} retry={examQuery.reload} /></div>
  return <div className="management-page exam-page">
    <PageHeader title="시험 관리" description={`${courseQuery.data?.title ?? '교육과정'}의 시험과 문항을 구성합니다.`}><Link className="admin-button" to={`/admin/courses/${courseId}`}>과정 상세로</Link></PageHeader>
    <Feedback error={error} success={success} />
    {!exam ? <section className="surface detail-section"><EmptyState message="등록된 시험이 없습니다. 시험은 선택 사항이며, 필요한 경우에만 추가하세요."><button className="admin-button primary-button" type="button" onClick={() => setExamModal(true)}>시험 만들기</button></EmptyState></section> : <>
      <section className="surface detail-section exam-overview" aria-labelledby="exam-title"><div className="section-heading"><div><h2 id="exam-title">{exam.title}</h2><p className="admin-hint">시험이 설정된 과정은 학습 진도와 시험 합격 조건을 모두 충족해야 수료됩니다.</p></div><div className="row-actions"><button className="admin-button" type="button" disabled={locked || pending} onClick={() => setExamModal(true)}>기본정보 수정</button><button className="admin-button" type="button" disabled={pending} onClick={() => void validateConfiguration()}>구성 검증</button></div></div>
        <dl className="exam-summary"><div><dt>합격 기준</dt><dd>{exam.passingScore}점 <small>/ 100점 환산</small></dd></div><div><dt>최대 응시</dt><dd>{exam.maxAttempts}회</dd></div><div><dt>문항</dt><dd>{questions.length}개</dd></div><div><dt>총 문항 배점</dt><dd>{totalQuestionScore(questions)}점</dd></div></dl>
        {locked && <p className="exam-lock-notice" role="status">시험 응시가 시작되어 시험 구성과 기본정보를 변경할 수 없습니다. 조회는 계속 가능합니다.</p>}
      </section>
      <section className="surface detail-section question-section" aria-labelledby="questions-heading"><div className="section-heading"><div><h2 id="questions-heading">문항</h2><p className="admin-hint">위·아래로 순서를 정한 뒤 전체 순서를 저장합니다. 정답 정보는 관리자 화면에만 표시됩니다.</p></div><button className="admin-button primary-button" type="button" disabled={locked || dirty || pending} title={locked ? '응시가 시작된 시험은 변경할 수 없습니다.' : dirty ? '순서를 먼저 저장하거나 되돌려 주세요.' : undefined} onClick={() => setQuestionModal('create')}>+ 문항 추가</button></div>
        {questionQuery.loading ? <LoadingState /> : questionQuery.error ? <ErrorState message={questionQuery.error} retry={questionQuery.reload} /> : !questions.length ? <EmptyState message="등록된 문항이 없습니다."><button className="admin-button primary-button" disabled={locked} onClick={() => setQuestionModal('create')}>문항 추가</button></EmptyState> : <><QuestionList questions={questions} orderedIds={orderedIds} locked={locked} pending={pending} onMove={(index, direction) => setDraftOrder(moveQuestion(orderedIds, index, direction))} onEdit={setQuestionModal} onDelete={question => { setError(''); setDeleting(question) }} /><div className="order-actions"><span>{dirty ? '저장하지 않은 순서 변경이 있습니다.' : '현재 문항 순서가 저장되어 있습니다.'}</span><div className="row-actions"><button className="admin-button" type="button" disabled={!dirty || pending} onClick={() => setDraftOrder(null)}>되돌리기</button><button className="admin-button primary-button" type="button" disabled={!dirty || pending || locked} onClick={() => void saveOrder()}>{pending ? '저장 중…' : '순서 저장'}</button></div></div></>}
      </section>
    </>}
    {examModal && <ExamModal courseId={courseId} exam={exam ?? undefined} onClose={() => setExamModal(false)} onLocked={() => setLocked(true)} onSaved={() => saved(exam ? '시험 정보를 수정했습니다.' : '시험을 등록했습니다.')} />}
    {questionModal && exam && <QuestionModal courseId={courseId} question={questionModal === 'create' ? undefined : questionModal} nextOrder={nextOrder} onClose={() => setQuestionModal(null)} onLocked={() => setLocked(true)} onSaved={() => saved(questionModal === 'create' ? '문항을 추가했습니다.' : '문항을 수정했습니다.')} />}
    {deleting && <ConfirmDialog title={`“${deleting.questionText}” 문항 삭제`} description="문항과 선택지를 삭제합니다. 응시 이력이 있으면 삭제할 수 없습니다." label="문항 삭제" pending={pending} error={error} onClose={() => setDeleting(null)} onConfirm={() => void removeQuestion()} />}
  </div>
}
