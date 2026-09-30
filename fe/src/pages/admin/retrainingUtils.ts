import type { RetrainingPolicy, RetrainingPolicyRequest } from '../../api/retrainingTypes.ts'

// RetrainingPolicy.configure의 잠금 조건. skip으로 소비한 회차도 포함한다.
export function scheduleLocked(policy?: RetrainingPolicy) {
  return !!policy && policy.nextOccurrenceNumber > 1
}

export function policyRequest(value: RetrainingPolicyRequest, original?: RetrainingPolicy): RetrainingPolicyRequest {
  const schedule = scheduleLocked(original) && original ? original : value
  return {
    sourceCourseId: schedule.sourceCourseId, baseTitle: value.baseTitle.trim(),
    enabled: value.enabled, autoCreate: value.autoCreate, autoOpen: value.autoOpen,
    intervalMonths: schedule.intervalMonths, firstStartDate: schedule.firstStartDate,
    durationDays: schedule.durationDays, generationLeadDays: schedule.generationLeadDays,
  }
}

export function policyValidation(value: RetrainingPolicyRequest) {
  if (!Number.isSafeInteger(value.sourceCourseId) || value.sourceCourseId < 1) return '기준 교육과정을 선택해 주세요.'
  if (!value.baseTitle.trim() || value.baseTitle.trim().length > 150) return '기본 제목은 1~150자로 입력해 주세요.'
  for (const [number, min, max] of [[value.intervalMonths, 1, 1200], [value.durationDays, 1, 3660], [value.generationLeadDays, 0, 3660]]) {
    if (!Number.isInteger(number) || number < min || number > max) return '반복 주기는 1~1200개월, 운영 기간은 1~3660일, 생성 선행 일수는 0~3660일의 정수로 입력해 주세요.'
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value.firstStartDate) || value.firstStartDate < '1000-01-01'
    || !Number.isFinite(Date.parse(value.firstStartDate))
    || new Date(value.firstStartDate).toISOString().slice(0, 10) !== value.firstStartDate) return '올바른 첫 시작일을 입력해 주세요.'
  return ''
}

export function occurrenceValidation(value: string) {
  const number = Number(value)
  return /^\d+$/.test(value) && Number.isInteger(number) && number > 0 && number <= 2147483647
    ? '' : '회차 번호는 1~2147483647 사이의 정수로 입력해 주세요.'
}
