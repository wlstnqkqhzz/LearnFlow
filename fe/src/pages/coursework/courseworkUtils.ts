import type { AuthUser } from '../../auth/authTypes.ts'
import type { Course } from '../../api/courseTypes.ts'
import type { AssignmentStatus, EmployeeAssignment, SubmissionRequest } from '../../api/courseworkTypes.ts'
import { safeResourceUrl } from '../employee/learningUtils.ts'

export const assignmentStatuses: Record<AssignmentStatus, { label: string; tone: string }> = {
  NOT_SUBMITTED: { label: '미제출', tone: 'neutral' },
  PENDING_GRADING: { label: '채점 대기', tone: 'warning' },
  PASSED: { label: '합격', tone: 'success' },
  FAILED: { label: '불합격', tone: 'danger' },
}
export function canManageAssignments(user: AuthUser | null, course: Course) {
  return !!user && (user.roles.includes('ADMIN') || (user.roles.includes('INSTRUCTOR') && course.instructorId === user.memberId))
}
export function canSubmitAssignment(item: EmployeeAssignment) {
  return item.status !== 'PASSED' && item.submittable && (!item.submission || item.resubmittable)
}
export function submissionNotice(item: EmployeeAssignment) {
  if (item.status === 'PASSED') return '합격한 과제는 재제출할 수 없습니다.'
  if (!canSubmitAssignment(item)) return '마감이 지났거나 종료된 수강으로 제출할 수 없습니다. 최신 상태를 확인해 주세요.'
  if (item.status === 'PENDING_GRADING') return '채점을 기다리고 있습니다. 마감 전에는 제출 내용을 바꿀 수 있습니다.'
  if (item.status === 'FAILED') return '과제를 보완해 다시 제출할 수 있습니다. 재제출하면 이전 점수와 피드백이 초기화됩니다.'
  return '텍스트 또는 URL로 과제를 제출해 주세요.'
}
export function submissionValidation(request: SubmissionRequest) {
  const content = request.content.trim()
  if (!content) return '제출 내용을 입력해 주세요.'
  if (content.length > 10000) return '제출 내용은 10,000자 이하여야 합니다.'
  if (request.submissionType === 'URL' && (content.length > 2048 || !safeResourceUrl(content))) return '2,048자 이하의 올바른 http/https URL을 입력해 주세요.'
  return ''
}
export function scoreText(score: number | null) { return score == null ? '미채점' : `${score.toFixed(2)}점` }
export function submissionTime(value: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', dateStyle: 'short', timeStyle: 'short' })
    .format(new Date(`${value}Z`))
}
