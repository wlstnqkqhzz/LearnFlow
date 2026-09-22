import { useState, type FormEvent } from 'react'
import { examApi } from '../../api/examApi.ts'
import { adminErrorCode, adminErrorMessage } from '../../api/adminError.ts'
import type { Exam, ExamCreateRequest } from '../../api/examTypes.ts'
import { Feedback, SubmitButton } from '../../components/admin/AdminUI.tsx'
import { Modal } from '../../components/admin/Modal.tsx'

export function ExamModal({ courseId, exam, onClose, onSaved, onLocked }: {
  courseId: number
  exam?: Exam
  onClose: () => void
  onSaved: () => void
  onLocked: () => void
}) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const request: ExamCreateRequest = {
      title: String(form.get('title') ?? '').trim(),
      passingScore: Number(form.get('passingScore')),
      maxAttempts: Number(form.get('maxAttempts')),
    }
    setPending(true); setError('')
    try {
      if (exam) await examApi.update(courseId, request)
      else await examApi.create(courseId, request)
      onSaved()
    } catch (cause) {
      if (adminErrorCode(cause) === 'EXAM_CONFIGURATION_LOCKED') onLocked()
      setError(adminErrorMessage(cause))
    } finally { setPending(false) }
  }
  return <Modal title={exam ? '시험 기본정보 수정' : '시험 만들기'} onClose={onClose} busy={pending}>
    <form className="admin-form" onSubmit={event => void submit(event)}>
      <Feedback error={error} />
      <fieldset className="form-grid" disabled={pending}>
        <label className="admin-field course-title-field">시험명<input name="title" required maxLength={200} pattern=".*\S.*" defaultValue={exam?.title ?? ''} /></label>
        <label className="admin-field">합격 기준 점수<input name="passingScore" type="number" required min="0" max="100" step="0.01" defaultValue={exam?.passingScore ?? 80} /><small>시험 제출 후 100점 기준으로 환산한 점수에 적용됩니다.</small></label>
        <label className="admin-field">최대 응시 횟수<input name="maxAttempts" type="number" required min="1" step="1" defaultValue={exam?.maxAttempts ?? 3} /></label>
      </fieldset>
      <div className="admin-actions"><button type="button" className="admin-button" disabled={pending} onClick={onClose}>취소</button><SubmitButton pending={pending} label={exam ? '시험 정보 저장' : '시험 만들기'} /></div>
    </form>
  </Modal>
}
