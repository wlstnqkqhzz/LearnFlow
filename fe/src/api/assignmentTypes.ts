import type { PageResponse } from './adminTypes.ts'

export type AssignmentRuleType = 'ALL_EMPLOYEES' | 'DEPARTMENT' | 'JOB_POSITION' | 'NEW_EMPLOYEE'
export type AssignmentRule = { id: number; courseId: number; ruleType: AssignmentRuleType; departmentId: number | null; jobPositionId: number | null; newEmployeeDays: number | null; active: boolean; createdAt: string; updatedAt: string }
export type AssignmentRuleRequest = { ruleType: AssignmentRuleType; departmentId: number | null; jobPositionId: number | null; newEmployeeDays: number | null }
export type AssignmentRuleCreateRequest = AssignmentRuleRequest & { active: boolean }

export type EnrollmentStatus = 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'EXPIRED'
export type AssignmentSource = 'MANUAL' | 'AUTOMATIC'
export type Enrollment = { enrollmentId: number; memberId: number; memberName: string; courseId: number; courseTitle: string; status: EnrollmentStatus; assignmentSource: AssignmentSource; assignmentRuleId: number | null; assignedAt: string; startedAt: string | null; completedAt: string | null; dueDate: string }
export type EnrollmentSearch = { page: number; size: number; status?: EnrollmentStatus }
export type EnrollmentPage = PageResponse<Enrollment>
