import type { CourseCreateRequest, CourseType } from '../../api/courseTypes.ts'

export function courseRequestFromForm(form: FormData): CourseCreateRequest {
  const description = String(form.get('description') ?? '').trim()
  const startDate = String(form.get('startDate') ?? '')
  const endDate = String(form.get('endDate') ?? '')
  const instructor = String(form.get('instructorId') ?? '')
  return {
    title: String(form.get('title') ?? '').trim(),
    description: description || null,
    courseType: String(form.get('courseType')) as CourseType,
    startDate: startDate || null,
    endDate: endDate || null,
    passingProgressRate: Number(form.get('passingProgressRate')),
    instructorId: instructor ? Number(instructor) : null,
  }
}

export function validateCourseRequest(request: CourseCreateRequest) {
  if (request.startDate && request.endDate && request.startDate > request.endDate) return '운영 시작일은 종료일보다 늦을 수 없습니다.'
  return ''
}
