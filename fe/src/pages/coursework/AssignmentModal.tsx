import { useState, type FormEvent } from 'react'
import type { Course } from '../../api/courseTypes.ts'
import type { Assignment, AssignmentRequest } from '../../api/courseworkTypes.ts'
import { courseworkApi } from '../../api/courseworkApi.ts'
import { Modal } from '../../components/admin/Modal.tsx'
import { Feedback, SubmitButton } from '../../components/admin/AdminUI.tsx'
import { useAction } from '../../hooks/useAction.ts'

export function AssignmentModal({ course, assignment, nextOrder, onClose, onSaved }: {
  course: Course; assignment?: Assignment; nextOrder: number; onClose: () => void; onSaved: () => void
}) {
  const action = useAction()
  const [validation, setValidation] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const request: AssignmentRequest = {
      title: String(data.get('title') ?? '').trim(), description: String(data.get('description') ?? '').trim() || null,
      required: data.get('required') === 'on', dueDate: String(data.get('dueDate')),
      passingScore: Number(data.get('passingScore')), sortOrder: assignment?.sortOrder ?? nextOrder,
    }
    if (!request.title) { setValidation('과제 제목을 입력해 주세요.'); return }
    setValidation('')
    const saved = await action.run(async () => {
      if (assignment) await courseworkApi.update(course.id, assignment.assignmentId, request)
      else await courseworkApi.create(course.id, request)
    }, '과제를 저장했습니다.')
    if (saved) onSaved()
  }
  return <Modal title={assignment ? '과제 수정' : '과제 추가'} busy={action.pending} onClose={onClose}>
    <form className="admin-form" onSubmit={event => void submit(event)}>
      <Feedback error={validation || action.error} />
      <fieldset className="form-grid" disabled={action.pending}>
        <label className="admin-field course-title-field">제목<input name="title" required maxLength={200} defaultValue={assignment?.title ?? ''} /></label>
        <label className="admin-field course-description-field">설명<textarea name="description" rows={5} maxLength={10000} defaultValue={assignment?.description ?? ''} /></label>
        <label className="admin-field">제출 마감일 (서울)<input name="dueDate" type="date" required min={course.startDate ?? undefined} max={course.endDate ?? undefined} defaultValue={assignment?.dueDate ?? course.endDate ?? ''} /></label>
        <label className="admin-field">통과 점수<input name="passingScore" type="number" required min="0" max="100" step="0.01" defaultValue={assignment?.passingScore ?? 80} /></label>
        <label className="check-field"><input name="required" type="checkbox" defaultChecked={assignment?.required ?? true} /> 수료에 필요한 필수 과제</label>
      </fieldset>
      <div className="admin-actions"><button type="button" className="admin-button" disabled={action.pending} onClick={onClose}>취소</button><SubmitButton pending={action.pending} label="과제 저장" /></div>
    </form>
  </Modal>
}
