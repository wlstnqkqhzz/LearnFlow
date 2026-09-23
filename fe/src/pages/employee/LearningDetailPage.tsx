import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { employeeLearningApi } from '../../api/employeeLearningApi.ts'
import type { LearningContentProgress, MyLearningDetail } from '../../api/learningTypes.ts'
import { ErrorState, LoadingState } from '../../components/admin/AdminUI.tsx'
import { ProgressBar } from '../../components/common/Progress.tsx'
import { useRemote } from '../../hooks/useRemote.ts'
import { LearningStatusBadge } from './LearningPage.tsx'
import { EmployeeExamSection } from './EmployeeExamSection.tsx'
import { dueLabel, isTerminal, learningContentTypes, learningCourseTypes, learningPeriod, utcDateInSeoul } from './learningUtils.ts'

export function LearningContentList({ detail }: { detail: MyLearningDetail }) {
  const contents = [...detail.contents].sort((a, b) => a.sortOrder - b.sortOrder)
  return <div className="learning-content-list">{contents.map((content, index) => <LearningContentRow key={content.contentId} enrollmentId={detail.enrollmentId} content={content} terminal={isTerminal(detail.status)} index={index + 1} />)}</div>
}
function LearningContentRow({ enrollmentId, content, terminal, index }: { enrollmentId: number; content: LearningContentProgress; terminal: boolean; index: number }) {
  const completed = Number(content.progressRate) === 100
  return <article className="learning-content-row"><span className="learning-content-number">{index}</span><div className="learning-content-copy"><div><strong>{content.title}</strong><span className="status-badge tone-neutral">{learningContentTypes[content.contentType]}</span><span className={`status-badge tone-${content.required ? 'primary' : 'neutral'}`}>{content.required ? '필수' : '선택'}</span></div><p>{completed ? '완료' : Number(content.progressRate) > 0 ? `진도 ${content.progressRate}%` : '학습 전'}</p><div className="content-progress"><ProgressBar value={Number(content.progressRate)} label={`${content.title} 학습 진도`} /><span>{content.progressRate}%</span></div></div><Link className={`admin-button${!terminal && !completed ? ' primary-button' : ''}`} to={`/employee/learning/${enrollmentId}/content/${content.contentId}`}>{terminal || completed ? '다시 보기' : '학습하기'}</Link></article>
}

export function LearningSummary({ detail }: { detail: MyLearningDetail }) {
  return <section className="surface learning-summary"><div className="learning-summary-title"><div className="learning-badges"><span className="status-badge tone-primary">{learningCourseTypes[detail.courseType]}</span><LearningStatusBadge status={detail.status} /></div><h2>{detail.courseTitle}</h2><p>{detail.courseDescription || '등록된 과정 설명이 없습니다.'}</p></div><dl><div><dt>운영 기간</dt><dd>{learningPeriod(detail.courseStartDate, detail.courseEndDate)}</dd></div><div><dt>마감</dt><dd>{dueLabel(detail.dueDate)}</dd></div><div><dt>강사</dt><dd>{detail.instructorName ?? '강사 미지정'}</dd></div>{detail.completedAt && <div><dt>수료일</dt><dd>{utcDateInSeoul(detail.completedAt)}</dd></div>}</dl></section>
}

export function LearningDetailPage() {
  const { enrollmentId } = useParams()
  const id = Number(enrollmentId)
  if (!Number.isSafeInteger(id) || id <= 0) return <div className="learning-page"><p role="alert">올바른 교육 주소가 아닙니다.</p></div>
  return <LearningDetail key={id} enrollmentId={id} />
}
function LearningDetail({ enrollmentId }: { enrollmentId: number }) {
  const query = useRemote(useCallback((signal: AbortSignal) => employeeLearningApi.detail(enrollmentId, signal), [enrollmentId]))
  if (query.loading) return <div className="learning-page"><LoadingState /></div>
  if (query.error) return <div className="learning-page"><ErrorState message={query.error} retry={query.reload} /></div>
  const detail = query.data!
  return <div className="learning-page"><div className="learning-page-actions"><Link className="admin-button" to="/employee/learning">내 교육으로</Link></div><LearningSummary detail={detail} /><section className="surface learning-progress-summary"><div><h2>나의 학습 현황</h2><p>필수 콘텐츠 평균 진도 기준</p></div><strong>{detail.progressRate}%</strong><ProgressBar value={Number(detail.progressRate)} label="수료 진도" /><span>수료 기준 {detail.passingProgressRate}%{detail.contentConditionSatisfied ? ' · 콘텐츠 조건 충족' : ''}</span></section><section className="surface learning-contents"><div className="section-heading"><div><h2>콘텐츠</h2><p className="admin-hint">필수 콘텐츠만 수료 진도에 반영됩니다.</p></div></div>{detail.contents.length ? <LearningContentList detail={detail} /> : <p className="admin-state">등록된 학습 콘텐츠가 없습니다.</p>}</section><EmployeeExamSection detail={detail} onRefresh={query.reload} /></div>
}
