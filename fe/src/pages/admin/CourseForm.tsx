import { useState, type FormEvent } from 'react'
import type { Course, CourseCreateRequest } from '../../api/courseTypes.ts'
import { Feedback, SubmitButton } from '../../components/admin/AdminUI.tsx'
import { courseTypes } from './courseUtils.ts'
import { courseRequestFromForm, validateCourseRequest } from './courseFormUtils.ts'

export function CourseForm({ course, pending, actionError, actionSuccess, submitLabel, onSubmit }: {
  course?: Course
  pending: boolean
  actionError?: string
  actionSuccess?: string
  submitLabel: string
  onSubmit: (request: CourseCreateRequest) => Promise<void>
}) {
  const [validation, setValidation] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const request = courseRequestFromForm(new FormData(event.currentTarget))
    const error = validateCourseRequest(request)
    setValidation(error)
    if (!error) await onSubmit(request)
  }
  return <form className="admin-form course-form" onSubmit={event => void submit(event)}>
    <Feedback error={validation || actionError} success={actionSuccess} />
    <fieldset className="form-grid" disabled={pending}>
      <label className="admin-field course-title-field">과정명<input name="title" required maxLength={200} pattern=".*\S.*" defaultValue={course?.title ?? ''} placeholder="교육과정명을 입력하세요" /></label>
      <label className="admin-field">과정 유형<select name="courseType" required defaultValue={course?.courseType ?? 'MANDATORY'}>{Object.entries(courseTypes).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="admin-field">운영 시작일<input name="startDate" type="date" defaultValue={course?.startDate ?? ''} /></label>
      <label className="admin-field">운영 종료일<input name="endDate" type="date" defaultValue={course?.endDate ?? ''} /></label>
      <label className="admin-field">수료 진도 기준 (%)<input name="passingProgressRate" type="number" min="0" max="100" step="0.01" required defaultValue={course?.passingProgressRate ?? 100} /></label>
      <label className="admin-field">강사 회원 ID <small>선택 사항 · INSTRUCTOR 역할 회원만 지정 가능</small><input name="instructorId" type="number" min="1" step="1" defaultValue={course?.instructorId ?? ''} placeholder="미지정" /></label>
      <label className="admin-field course-description-field">과정 설명<textarea name="description" maxLength={16383} rows={5} defaultValue={course?.description ?? ''} placeholder="과정에 대한 설명을 입력하세요" /></label>
    </fieldset>
    <p className="admin-hint">초안은 날짜와 강사 없이 저장할 수 있습니다. 과정 오픈에는 시작일과 종료일만 필요합니다.</p>
    <div className="admin-actions"><SubmitButton pending={pending} label={submitLabel} /></div>
  </form>
}
