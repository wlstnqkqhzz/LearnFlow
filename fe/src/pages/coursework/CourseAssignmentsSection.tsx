import { useCallback, useState } from 'react'
import type { Course } from '../../api/courseTypes.ts'
import type { Assignment } from '../../api/courseworkTypes.ts'
import { courseworkApi } from '../../api/courseworkApi.ts'
import { useAuth } from '../../auth/AuthContext.ts'
import { Feedback } from '../../components/admin/AdminUI.tsx'
import { ConfirmDialog } from '../../components/admin/Modal.tsx'
import { useAction } from '../../hooks/useAction.ts'
import { useRemote } from '../../hooks/useRemote.ts'
import { moveContent } from '../admin/courseUtils.ts'
import { AssignmentModal } from './AssignmentModal.tsx'
import { canManageAssignments } from './courseworkUtils.ts'
import { CourseworkState } from './CourseworkUI.tsx'
import { SubmissionsSection } from './SubmissionsSection.tsx'

export function AssignmentList({ assignments, editable, busy, dirty, onEdit, onDelete, onMove }: {
  assignments: Assignment[]; editable: boolean; busy: boolean; dirty: boolean
  onEdit: (item: Assignment) => void; onDelete: (item: Assignment) => void; onMove: (index: number, direction: -1 | 1) => void
}) {
  return <ol className="coursework-list">{assignments.map((item, index) => <li className="coursework-card" key={item.assignmentId}>
    <div className="section-heading"><div className="coursework-copy"><h3>{index + 1}. {item.title}</h3><span className={`status-badge tone-${item.required ? 'primary' : 'neutral'}`}>{item.required ? '필수' : '선택'}</span></div>
      {editable && <div className="row-actions"><button className="admin-button" disabled={busy || index === 0} onClick={() => onMove(index, -1)} aria-label={`${item.title} 위로 이동`}>↑</button><button className="admin-button" disabled={busy || index === assignments.length - 1} onClick={() => onMove(index, 1)} aria-label={`${item.title} 아래로 이동`}>↓</button><button className="admin-button" disabled={busy || dirty} onClick={() => onEdit(item)}>수정</button><button className="admin-button danger-button" disabled={busy || dirty} onClick={() => onDelete(item)}>삭제</button></div>}
    </div>
    <p className="coursework-copy">{item.description || '등록된 설명이 없습니다.'}</p>
    <p className="admin-hint">마감 {item.dueDate} (서울) · 통과 기준 {item.passingScore}점</p>
  </li>)}</ol>
}
export function CourseAssignmentsSection({ course }: { course: Course }) {
  const { user } = useAuth()
  if (!canManageAssignments(user, course)) return <section className="surface detail-section"><p role="alert">이 과정의 과제를 관리할 권한이 없습니다.</p></section>
  return <AssignmentManager key={`${course.id}:${course.status}`} course={course} />
}
function AssignmentManager({ course }: { course: Course }) {
  const query = useRemote(useCallback((signal: AbortSignal) => courseworkApi.list(course.id, signal), [course.id]))
  const action = useAction()
  const [order, setOrder] = useState<number[] | null>(null)
  const [modal, setModal] = useState<Assignment | 'create' | null>(null)
  const [deleting, setDeleting] = useState<Assignment | null>(null)
  const editable = course.status === 'DRAFT'
  const original = [...(query.data ?? [])].sort((a, b) => a.sortOrder - b.sortOrder)
  const byId = new Map(original.map(item => [item.assignmentId, item]))
  const ids = order ?? original.map(item => item.assignmentId)
  const items = ids.map(id => byId.get(id)).filter((item): item is Assignment => !!item)
  const dirty = order !== null && ids.join(',') !== original.map(item => item.assignmentId).join(',')
  const busy = action.pending || query.loading || !!query.error
  function refresh() { setOrder(null); query.reload() }
  return <>
    <section className="surface detail-section coursework-stack" aria-label="과제 관리">
      <div className="section-heading"><div><h2>과제</h2><p className="admin-hint">{editable ? '과제를 구성하고 위·아래 버튼으로 순서를 변경합니다.' : '공개된 과정의 과제 구성은 변경할 수 없습니다.'}</p></div><div className="row-actions"><button className="admin-button" disabled={action.pending || query.loading} onClick={refresh}>새로고침</button>{editable && <button className="admin-button primary-button" disabled={busy || dirty} onClick={() => setModal('create')}>+ 과제 추가</button>}</div></div>
      <Feedback error={!deleting ? action.error : ''} success={action.success} />
      <CourseworkState loading={query.loading} error={query.error} empty={!items.length} emptyMessage="등록된 과제가 없습니다." retry={refresh}>
        <AssignmentList assignments={items} editable={editable} busy={busy} dirty={dirty} onEdit={setModal} onDelete={item => { action.clear(); setDeleting(item) }} onMove={(index, direction) => setOrder(moveContent(ids, index, direction))} />
      </CourseworkState>
      {editable && dirty && <div className="order-actions"><span>저장하지 않은 순서 변경이 있습니다.</span><div className="row-actions"><button className="admin-button" disabled={busy} onClick={() => setOrder(null)}>되돌리기</button><button className="admin-button primary-button" disabled={busy} onClick={() => void action.run(async () => { await courseworkApi.reorder(course.id, ids); refresh() }, '과제 순서를 저장했습니다.')}>순서 저장</button></div></div>}
      {editable && modal && <AssignmentModal course={course} assignment={modal === 'create' ? undefined : modal} nextOrder={Math.max(0, ...original.map(item => item.sortOrder)) + 1} onClose={() => setModal(null)} onSaved={() => { setModal(null); refresh() }} />}
      {editable && deleting && <ConfirmDialog title="과제 삭제" description={`“${deleting.title}” 과제를 삭제하시겠습니까?`} label="삭제" pending={action.pending} error={action.error} onClose={() => setDeleting(null)} onConfirm={() => void action.run(async () => { await courseworkApi.remove(course.id, deleting.assignmentId); setDeleting(null); refresh() }, '과제를 삭제했습니다.')} />}
    </section>
    {query.data && !query.error && <SubmissionsSection courseId={course.id} assignments={query.data} />}
  </>
}
