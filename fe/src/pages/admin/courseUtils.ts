import type { Course, CourseStatus, CourseType, ContentType } from '../../api/courseTypes.ts'

export const courseTypes: Record<CourseType, string> = { MANDATORY: '필수교육', OPTIONAL: '선택교육' }
export const courseStatuses: Record<CourseStatus, { label: string; tone: string }> = {
  DRAFT: { label: '초안', tone: 'neutral' },
  OPEN: { label: '운영 중', tone: 'success' },
  CLOSED: { label: '종료', tone: 'neutral' },
}
export const contentTypes: Record<ContentType, string> = { VIDEO: '동영상', DOCUMENT: '문서', LINK: '링크' }

export function coursePeriod(course: Pick<Course, 'startDate' | 'endDate'>) {
  if (!course.startDate && !course.endDate) return '기간 미정'
  return `${course.startDate ?? '미정'} ~ ${course.endDate ?? '미정'}`
}

export function nextCourseStatus(status: CourseStatus): 'OPEN' | 'CLOSED' | null {
  if (status === 'DRAFT') return 'OPEN'
  if (status === 'OPEN') return 'CLOSED'
  return null
}

export function openValidation(course: Pick<Course, 'startDate' | 'endDate'>) {
  if (!course.startDate || !course.endDate) return '과정을 오픈하려면 운영 시작일과 종료일을 입력해 주세요.'
  if (course.startDate > course.endDate) return '운영 시작일은 종료일보다 늦을 수 없습니다.'
  return ''
}

export function moveContent(ids: number[], index: number, direction: -1 | 1) {
  const target = index + direction
  if (target < 0 || target >= ids.length) return ids
  const next = [...ids]
  ;[next[index], next[target]] = [next[target], next[index]]
  return next
}
