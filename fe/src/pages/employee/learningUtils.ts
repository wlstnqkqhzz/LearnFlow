import type { EnrollmentStatus } from '../../api/assignmentTypes.ts'
import type { CourseType, ContentType } from '../../api/courseTypes.ts'

export const learningStatuses: Record<EnrollmentStatus, { label: string; tone: string }> = {
  ASSIGNED: { label: '학습 전', tone: 'neutral' },
  IN_PROGRESS: { label: '학습 중', tone: 'primary' },
  COMPLETED: { label: '수료', tone: 'success' },
  FAILED: { label: '실패', tone: 'danger' },
  EXPIRED: { label: '만료', tone: 'warning' },
}
export const learningCourseTypes: Record<CourseType, string> = { MANDATORY: '필수교육', OPTIONAL: '선택교육' }
export const learningContentTypes: Record<ContentType, string> = { VIDEO: '동영상', DOCUMENT: '문서', LINK: '링크' }
export const terminalStatuses: EnrollmentStatus[] = ['COMPLETED', 'FAILED', 'EXPIRED']

export function isTerminal(status: EnrollmentStatus) { return terminalStatuses.includes(status) }
export function learningPeriod(start: string | null, end: string | null) {
  if (!start && !end) return '기간 미정'
  return `${start ?? '미정'} ~ ${end ?? '미정'}`
}
export function dueLabel(dueDate: string, now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' })
    .formatToParts(now)
    .reduce<Record<string, number>>((result, part) => {
      if (part.type === 'year' || part.type === 'month' || part.type === 'day') result[part.type] = Number(part.value)
      return result
    }, {})
  const today = Date.UTC(parts.year, parts.month - 1, parts.day)
  const [year, month, day] = dueDate.split('-').map(Number)
  const days = Math.round((Date.UTC(year, month - 1, day) - today) / 86_400_000)
  if (days === 0) return '마감 D-Day'
  if (days > 0 && days <= 7) return `마감 D-${days}`
  return `마감 ${dueDate}`
}

export function safeResourceUrl(value: string) {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null
  } catch {
    return null
  }
}

export function utcDateInSeoul(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(`${value}Z`))
}
