import { useState, type FormEvent } from 'react'
import { courseContentApi } from '../../api/courseContentApi.ts'
import type { ContentType, CourseContent, CourseContentCreateRequest } from '../../api/courseTypes.ts'
import { Feedback, SubmitButton } from '../../components/admin/AdminUI.tsx'
import { Modal } from '../../components/admin/Modal.tsx'
import { useAction } from '../../hooks/useAction.ts'
import { contentTypes } from './courseUtils.ts'

export function CourseContentModal({ courseId, content, nextOrder, onClose, onSaved }: {
  courseId: number
  content?: CourseContent
  nextOrder: number
  onClose: () => void
  onSaved: () => void
}) {
  const action = useAction()
  const [validation, setValidation] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const duration = String(form.get('durationSeconds') ?? '')
    const request: CourseContentCreateRequest = {
      title: String(form.get('title') ?? '').trim(),
      contentType: String(form.get('contentType')) as ContentType,
      contentUrl: String(form.get('contentUrl') ?? '').trim(),
      durationSeconds: duration ? Number(duration) : null,
      sortOrder: content?.sortOrder ?? nextOrder,
      required: form.get('required') === 'on',
    }
    if (!request.contentUrl) { setValidation('콘텐츠 주소를 입력해 주세요.'); return }
    setValidation('')
    const saved = await action.run(async () => {
      if (content) {
        await courseContentApi.update(courseId, content.id, {
          title: request.title,
          contentType: request.contentType,
          contentUrl: request.contentUrl,
          durationSeconds: request.durationSeconds,
          required: request.required,
        })
      } else await courseContentApi.create(courseId, request)
    }, content ? '콘텐츠를 수정했습니다.' : '콘텐츠를 추가했습니다.')
    if (saved) onSaved()
  }
  return <Modal title={content ? '콘텐츠 수정' : '콘텐츠 추가'} onClose={onClose} busy={action.pending}>
    <form className="admin-form" onSubmit={event => void submit(event)}>
      <Feedback error={validation || action.error} />
      <fieldset className="form-grid" disabled={action.pending}>
        <label className="admin-field course-title-field">콘텐츠명<input name="title" required maxLength={200} pattern=".*\S.*" defaultValue={content?.title ?? ''} /></label>
        <label className="admin-field">콘텐츠 유형<select name="contentType" required defaultValue={content?.contentType ?? 'VIDEO'}>{Object.entries(contentTypes).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label className="admin-field course-description-field">콘텐츠 주소<input name="contentUrl" type="url" required maxLength={2048} defaultValue={content?.contentUrl ?? ''} placeholder="https://" /></label>
        <label className="admin-field">재생 길이 (초)<input name="durationSeconds" type="number" min="0" step="1" defaultValue={content?.durationSeconds ?? ''} placeholder="선택 사항" /></label>
        <label className="check-field"><input name="required" type="checkbox" defaultChecked={content?.required ?? true} /> 수료 진도에 포함하는 필수 콘텐츠</label>
      </fieldset>
      {!content && <p className="admin-hint">목록 마지막 순서({nextOrder})로 추가됩니다. 추가 후 위·아래 버튼으로 순서를 바꿀 수 있습니다.</p>}
      <div className="admin-actions"><button type="button" className="admin-button" disabled={action.pending} onClick={onClose}>취소</button><SubmitButton pending={action.pending} label={content ? '콘텐츠 저장' : '콘텐츠 추가'} /></div>
    </form>
  </Modal>
}
