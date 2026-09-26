import { useCallback, useMemo, useState } from 'react'
import { courseContentApi } from '../../api/courseContentApi.ts'
import type { CourseContent } from '../../api/courseTypes.ts'
import { ContentTypeBadge } from '../../components/admin/CourseBadges.tsx'
import { EmptyState, ErrorState, Feedback, LoadingState } from '../../components/admin/AdminUI.tsx'
import { ConfirmDialog } from '../../components/admin/Modal.tsx'
import { useAction } from '../../hooks/useAction.ts'
import { useRemote } from '../../hooks/useRemote.ts'
import { moveContent } from './courseUtils.ts'
import { CourseContentModal } from './CourseContentModal.tsx'

export function CourseContentsSection({ courseId }: { courseId: number }) {
  const loader = useCallback((signal: AbortSignal) => courseContentApi.list(courseId, signal), [courseId])
  const query = useRemote(loader)
  const action = useAction()
  const [draftOrder, setDraftOrder] = useState<number[] | null>(null)
  const [modal, setModal] = useState<'create' | CourseContent | null>(null)
  const [deleting, setDeleting] = useState<CourseContent | null>(null)
  const byId = useMemo(() => new Map(query.data?.map(item => [item.id, item]) ?? []), [query.data])
  const original = query.data?.map(item => item.id) ?? []
  const orderedIds = draftOrder ?? original
  const ordered = orderedIds.map(id => byId.get(id)).filter((item): item is CourseContent => !!item)
  const dirty = draftOrder !== null && orderedIds.join(',') !== original.join(',')
  const nextOrder = Math.max(0, ...(query.data?.map(item => item.sortOrder) ?? [])) + 1

  async function saveOrder() {
    await action.run(async () => {
      await courseContentApi.reorder(courseId, orderedIds)
      setDraftOrder(null)
      query.reload()
    }, '콘텐츠 순서를 변경했습니다.')
  }
  async function remove() {
    if (!deleting) return
    const removed = await action.run(async () => { await courseContentApi.delete(courseId, deleting.id) }, '콘텐츠를 삭제했습니다.')
    if (removed) { setDeleting(null); setDraftOrder(null); query.reload() }
  }
  function saved() { setModal(null); setDraftOrder(null); query.reload() }

  return <section className="surface detail-section course-content-section" aria-labelledby="course-contents-heading">
    <div className="section-heading"><div><h2 id="course-contents-heading">콘텐츠</h2><p className="admin-hint">위·아래로 배치한 뒤 전체 순서를 한 번에 저장합니다.</p></div><button className="admin-button primary-button" type="button" disabled={dirty || action.pending || query.loading || !!query.error} title={dirty ? '순서를 먼저 저장하거나 되돌려 주세요.' : undefined} onClick={() => setModal('create')}>+ 콘텐츠 추가</button></div>
    <Feedback error={!deleting ? action.error : ''} success={action.success} />
    {query.loading ? <LoadingState /> : query.error ? <ErrorState message={query.error} retry={query.reload} /> : !ordered.length ? <EmptyState message="등록된 콘텐츠가 없습니다."><button className="admin-button primary-button" onClick={() => setModal('create')}>콘텐츠 추가</button></EmptyState> : <>
      <div className="table-scroll" tabIndex={0} role="region" aria-label="콘텐츠 목록"><table className="admin-table content-table"><thead><tr><th scope="col">순서</th><th scope="col">콘텐츠</th><th scope="col">유형</th><th scope="col">재생 길이</th><th scope="col">필수 여부</th><th scope="col">순서 변경</th><th scope="col">관리</th></tr></thead><tbody>{ordered.map((content, index) => <tr key={content.id}><td>{index + 1}</td><th scope="row">{content.title}<small className="table-secondary content-url" title={content.contentUrl}>{content.contentUrl}</small></th><td><ContentTypeBadge type={content.contentType} /></td><td>{content.durationSeconds == null ? '미지정' : `${content.durationSeconds}초`}</td><td><span className={`status-badge tone-${content.required ? 'success' : 'neutral'}`}>{content.required ? '필수' : '선택'}</span></td><td><span className="row-actions"><button type="button" className="admin-button icon-order-button" title="위로 이동" aria-label={`${content.title} 위로 이동`} disabled={index === 0 || action.pending} onClick={() => setDraftOrder(ids => moveContent(ids ?? original, index, -1))}>↑</button><button type="button" className="admin-button icon-order-button" title="아래로 이동" aria-label={`${content.title} 아래로 이동`} disabled={index === ordered.length - 1 || action.pending} onClick={() => setDraftOrder(ids => moveContent(ids ?? original, index, 1))}>↓</button></span></td><td><span className="row-actions"><button type="button" className="admin-button" disabled={action.pending || dirty} onClick={() => setModal(content)}>수정</button><button type="button" className="admin-button danger-button" disabled={action.pending || dirty} onClick={() => { action.clear(); setDeleting(content) }}>삭제</button></span></td></tr>)}</tbody></table></div>
      <div className="order-actions"><span>{dirty ? '저장하지 않은 순서 변경이 있습니다.' : '현재 순서가 저장되어 있습니다.'}</span><div className="row-actions"><button type="button" className="admin-button" disabled={!dirty || action.pending} onClick={() => setDraftOrder(null)}>되돌리기</button><button type="button" className="admin-button primary-button" disabled={!dirty || action.pending} onClick={() => void saveOrder()}>{action.pending ? '저장 중…' : '순서 저장'}</button></div></div>
    </>}
    {modal && <CourseContentModal courseId={courseId} content={modal === 'create' ? undefined : modal} nextOrder={nextOrder} onClose={() => setModal(null)} onSaved={saved} />}
    {deleting && <ConfirmDialog title={`“${deleting.title}” 콘텐츠 삭제`} description="콘텐츠를 삭제하시겠습니까? 학습 이력이 있으면 삭제가 거부될 수 있습니다." label="콘텐츠 삭제" pending={action.pending} error={action.error} onClose={() => setDeleting(null)} onConfirm={() => void remove()} />}
  </section>
}
