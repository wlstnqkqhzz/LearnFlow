import type { PageResponse } from './adminTypes.ts'

export type SubmissionType = 'TEXT' | 'URL'
export type AssignmentStatus = 'NOT_SUBMITTED' | 'PENDING_GRADING' | 'PASSED' | 'FAILED'
export type Assignment = {
  assignmentId: number; courseId: number; title: string; description: string | null
  required: boolean; dueDate: string; passingScore: number; sortOrder: number
  createdAt: string; updatedAt: string
}
export type AssignmentRequest = Pick<Assignment, 'title' | 'description' | 'required' | 'dueDate' | 'passingScore' | 'sortOrder'>
export type Submission = {
  submissionId: number; assignmentId: number; enrollmentId: number; memberId: number; memberName: string
  submissionType: SubmissionType; content: string; submittedAt: string; submissionCount: number
  status: Exclude<AssignmentStatus, 'NOT_SUBMITTED'>; score: number | null; passed: boolean | null
  feedback: string | null; gradedAt: string | null; gradedByMemberId: number | null; gradedByName: string | null; version: number
}
export type EmployeeAssignment = {
  assignment: Assignment; status: AssignmentStatus; effectiveDueDate: string
  submittable: boolean; resubmittable: boolean; submission: Submission | null
}
export type SubmissionRequest = { submissionType: SubmissionType; content: string }
export type GradeRequest = { score: number; feedback: string | null }
export type SubmissionSearch = {
  page: number; size: number; assignmentId?: number; enrollmentId?: number; memberId?: number
  status?: Exclude<AssignmentStatus, 'NOT_SUBMITTED'>
}
export type SubmissionPage = PageResponse<Submission>
