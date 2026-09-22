import type { Department, JobPosition, MemberStatus } from '../../api/adminTypes.ts'
import type { AssignmentRule, AssignmentRuleRequest, AssignmentRuleType, AssignmentSource, EnrollmentStatus } from '../../api/assignmentTypes.ts'

export const ruleTypes: Record<AssignmentRuleType, string> = { ALL_EMPLOYEES: '전체 직원', DEPARTMENT: '부서', JOB_POSITION: '직무', NEW_EMPLOYEE: '신입사원' }
export const enrollmentStatuses: Record<EnrollmentStatus, { label: string; tone: string }> = { ASSIGNED: { label: '배정됨', tone: 'neutral' }, IN_PROGRESS: { label: '학습 중', tone: 'primary' }, COMPLETED: { label: '수료', tone: 'success' }, FAILED: { label: '실패', tone: 'danger' }, EXPIRED: { label: '만료', tone: 'warning' } }
export const assignmentSources: Record<AssignmentSource, string> = { MANUAL: '수동 배정', AUTOMATIC: '자동 배정' }

export function ruleRequest(type: AssignmentRuleType, value?: number): AssignmentRuleRequest {
  return { ruleType: type, departmentId: type === 'DEPARTMENT' ? value ?? null : null, jobPositionId: type === 'JOB_POSITION' ? value ?? null : null, newEmployeeDays: type === 'NEW_EMPLOYEE' ? value ?? null : null }
}
export function ruleTarget(rule: AssignmentRule, departments: Department[], jobs: JobPosition[]) {
  if (rule.ruleType === 'ALL_EMPLOYEES') return '모든 재직 직원'
  if (rule.ruleType === 'DEPARTMENT') return departments.find(item => item.id === rule.departmentId)?.name ?? `부서 #${rule.departmentId}`
  if (rule.ruleType === 'JOB_POSITION') return jobs.find(item => item.id === rule.jobPositionId)?.name ?? `직무 #${rule.jobPositionId}`
  return `입사 후 ${rule.newEmployeeDays}일 이내`
}
export function eligibleForManualAssignment(status: MemberStatus) { return status !== 'RESIGNED' }
