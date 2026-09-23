import type { PageResponse } from './adminTypes.ts'
import type { ContentType, CourseType } from './courseTypes.ts'
import type { EnrollmentStatus } from './assignmentTypes.ts'

export type MyEnrollment = {
  enrollmentId: number
  memberId: number
  memberName: string
  courseId: number
  courseTitle: string
  courseType: CourseType
  courseStartDate: string | null
  courseEndDate: string | null
  status: EnrollmentStatus
  assignmentSource: 'MANUAL' | 'AUTOMATIC'
  assignmentRuleId: number | null
  assignedAt: string
  startedAt: string | null
  completedAt: string | null
  dueDate: string
}

export type MyEnrollmentSearch = { page: number; size: number; status?: EnrollmentStatus }
export type MyEnrollmentPage = PageResponse<MyEnrollment>

export type LearningContentProgress = {
  contentId: number
  title: string
  contentType: ContentType
  contentUrl: string
  durationSeconds: number | null
  required: boolean
  sortOrder: number
  progressRate: number
  completedAt: string | null
}

export type MyLearningDetail = {
  enrollmentId: number
  status: EnrollmentStatus
  dueDate: string
  assignedAt: string
  startedAt: string | null
  completedAt: string | null
  courseId: number
  courseTitle: string
  courseDescription: string | null
  courseType: CourseType
  courseStartDate: string | null
  courseEndDate: string | null
  instructorId: number | null
  instructorName: string | null
  progressRate: number
  passingProgressRate: number
  contentConditionSatisfied: boolean
  contents: LearningContentProgress[]
}
