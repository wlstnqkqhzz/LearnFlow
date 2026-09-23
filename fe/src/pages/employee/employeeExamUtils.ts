import type { EmployeeExamAttempt, EmployeeQuestionType } from '../../api/employeeExamTypes.ts'
import type { EnrollmentStatus } from '../../api/assignmentTypes.ts'
import { adminErrorCode, adminErrorMessage } from '../../api/adminError.ts'
import { isTerminal } from './learningUtils.ts'

export const employeeQuestionLabels: Record<EmployeeQuestionType, string> = {
  SINGLE_CHOICE: '단일 선택', MULTIPLE_CHOICE: '복수 선택', TRUE_FALSE: 'O / X',
}
export function selectChoice(type: EmployeeQuestionType, selected: number[], choiceId: number) {
  return type === 'MULTIPLE_CHOICE'
    ? selected.includes(choiceId) ? selected.filter(id => id !== choiceId) : [...selected, choiceId]
    : [choiceId]
}
export function examAction(status: EnrollmentStatus, history: EmployeeExamAttempt[], maxAttempts: number) {
  const ordered = [...history].sort((a, b) => a.attemptNumber - b.attemptNumber)
  const passed = ordered.find(item => item.passed === true && item.submittedAt !== null)
  const open = ordered.find(item => item.submittedAt === null)
  const latest = ordered.at(-1)
  const remaining = latest?.remainingAttempts ?? maxAttempts
  return { passed, open, latest, remaining, canStart: !isTerminal(status) && !passed && !open && remaining > 0,
    canContinue: !isTerminal(status) && !passed && !!open }
}
export function scoreLabel(value: number | null) { return value === null ? '미제출' : `${value.toFixed(2)}점` }
export function submissionDescription(total: number, answered: number) {
  return `전체 ${total}문항 · 답변 완료 ${answered}문항 · 미응답 ${total - answered}문항. 미응답 문항은 0점 처리됩니다. 제출 후에는 답안을 수정할 수 없습니다.`
}
export function examErrorMessage(error: unknown) {
  const messages: Record<string, string> = {
    EXAM_ACCESS_DENIED: '본인의 시험 응시만 확인할 수 있습니다.',
    EXAM_ATTEMPT_NOT_FOUND: '시험 응시 내역을 찾을 수 없습니다.',
    ENROLLMENT_EXAM_NOT_EDITABLE: '종료된 교육에서는 시험을 진행할 수 없습니다.',
    EXAM_ALREADY_PASSED: '이미 합격한 시험입니다. 결과를 확인해 주세요.',
    EXAM_ATTEMPTS_EXHAUSTED: '시험 응시 기회를 모두 사용했습니다.',
    INVALID_EXAM_CONFIGURATION: '현재 시험 구성이 완료되지 않았습니다.',
    INVALID_EXAM_ANSWER: '선택한 답안을 저장할 수 없습니다. 문항과 선택지를 확인해 주세요.',
    EXAM_ATTEMPT_ALREADY_SUBMITTED: '이미 제출된 시험입니다. 최신 결과를 확인해 주세요.',
    EXAM_ATTEMPT_NOT_SUBMITTED: '아직 제출되지 않은 시험입니다.',
  }
  return messages[adminErrorCode(error) ?? ''] ?? adminErrorMessage(error)
}
