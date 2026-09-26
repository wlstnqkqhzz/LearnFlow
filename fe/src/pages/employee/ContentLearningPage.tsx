import { useCallback, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { adminErrorMessage } from '../../api/adminError.ts'
import { employeeLearningApi } from '../../api/employeeLearningApi.ts'
import type { MyLearningDetail } from '../../api/learningTypes.ts'
import { ErrorState, Feedback, LoadingState } from '../../components/admin/AdminUI.tsx'
import { ProgressBar } from '../../components/common/Progress.tsx'
import { useRemote } from '../../hooks/useRemote.ts'
import { LearningStatusBadge } from './LearningPage.tsx'
import { isTerminal, learningContentTypes, safeResourceUrl } from './learningUtils.ts'

export function ContentLearningView({ detail, contentId, pending, feedback, error, onComplete }: { detail: MyLearningDetail; contentId: number; pending: boolean; feedback: string; error: string; onComplete: () => void }) {
  const content = detail.contents.find(item => item.contentId === contentId)
  if (!content) return <div className="surface learning-content-view"><p role="alert">이 교육에 포함된 콘텐츠를 찾을 수 없습니다.</p></div>
  const completed = Number(content.progressRate) === 100
  const editable = !isTerminal(detail.status)
  const resourceUrl = safeResourceUrl(content.contentUrl)
  const actionLabel = content.contentType === 'VIDEO' ? '동영상 열기' : content.contentType === 'DOCUMENT' ? '문서 열기' : '링크 열기'
  return <><section className="surface learning-content-view"><div className="learning-badges"><span className="status-badge tone-neutral">{learningContentTypes[content.contentType]}</span><span className={`status-badge tone-${content.required ? 'primary' : 'neutral'}`}>{content.required ? '필수' : '선택'}</span><LearningStatusBadge status={detail.status} /></div><h2>{content.title}</h2><p>외부 자료를 열어 학습한 뒤 학습 완료를 선택해 주세요.</p>{resourceUrl ? <a className="admin-button primary-button resource-link" href={resourceUrl} target="_blank" rel="noopener noreferrer">{actionLabel} <span className="sr-only">(새 탭)</span></a> : <p role="alert" className="terminal-notice">유효한 학습 자료 주소가 아닙니다.</p>}</section><section className="surface content-completion"><div><h2>나의 진도</h2><p>{completed ? '학습을 완료했습니다.' : `${content.progressRate}% 진행했습니다.`}</p></div><div className="content-completion-progress"><ProgressBar value={Number(content.progressRate)} label={`${content.title} 학습 진도`} /><strong>{content.progressRate}%</strong></div><Feedback error={error} success={feedback} />{editable && !completed && <button className="admin-button primary-button complete-learning-button" type="button" disabled={pending} onClick={onComplete}>{pending ? '저장 중…' : '학습 완료'}</button>}{!editable && <p className="terminal-notice">{detail.status === 'COMPLETED' ? '수료한 교육은 읽기 전용으로 제공됩니다.' : detail.status === 'FAILED' ? '실패 처리된 교육은 진도를 변경할 수 없습니다.' : '교육 기간이 만료되어 진도를 변경할 수 없습니다.'}</p>}</section></>
}

export function ContentLearningPage() {
  const params = useParams()
  const enrollmentId = Number(params.enrollmentId)
  const contentId = Number(params.contentId)
  if (![enrollmentId, contentId].every(value => Number.isSafeInteger(value) && value > 0)) return <div className="learning-page"><p role="alert">올바른 학습 주소가 아닙니다.</p></div>
  return <ContentLearning key={`${enrollmentId}-${contentId}`} enrollmentId={enrollmentId} contentId={contentId} />
}
function ContentLearning({ enrollmentId, contentId }: { enrollmentId: number; contentId: number }) {
  const query = useRemote(useCallback((signal: AbortSignal) => employeeLearningApi.detail(enrollmentId, signal), [enrollmentId]))
  const [detail, setDetail] = useState<MyLearningDetail | null>(null)
  const [pending, setPending] = useState(false)
  const busy = useRef(false)
  const [error, setError] = useState('')
  const [feedback, setFeedback] = useState('')
  const current = detail ?? query.data
  async function complete() {
    if (busy.current) return
    busy.current = true
    setPending(true); setError(''); setFeedback('')
    try {
      const updated = await employeeLearningApi.completeContent(enrollmentId, contentId)
      setDetail(updated)
      setFeedback(updated.status === 'COMPLETED' ? '교육과정을 수료했습니다.' : '학습을 완료했습니다.')
    } catch (cause) { setError(adminErrorMessage(cause)) }
    finally { busy.current = false; setPending(false) }
  }
  if (query.loading) return <div className="learning-page"><LoadingState /></div>
  if (query.error) return <div className="learning-page"><ErrorState message={query.error} retry={query.reload} /></div>
  return <div className="learning-page"><div className="learning-page-actions"><Link className="admin-button" to={`/employee/learning/${enrollmentId}`}>교육으로 돌아가기</Link></div><p className="learning-course-caption">{current!.courseTitle}</p><ContentLearningView detail={current!} contentId={contentId} pending={pending} feedback={feedback} error={error} onComplete={() => void complete()} /></div>
}
