import type { PageResponse } from './adminTypes.ts'
import type { CourseStatus } from './courseTypes.ts'

export type StatisticsFilter = { startDate: string; endDate: string; courseId?: number; departmentId?: number }
export type StatisticsMeta = Omit<StatisticsFilter, 'courseId' | 'departmentId'> & { courseId: number | null; departmentId: number | null; timeZone: string; generatedAt: string; departmentScope: 'DIRECT' }
export type StatisticsOverview = {
  meta: StatisticsMeta; assignedCount: number; completedCount: number
  cohortCompletedCount: number; cohortFailedCount: number; cohortExpiredCount: number
  completionRate: number | null; averageCompletionDays: number | null
}
export type StatisticsExam = { examId: number; submittedAttemptCount: number; passedAttemptCount: number; averageScore: number | null; attemptPassRate: number | null }
export type StatisticsCourse = {
  courseId: number; title: string; status: CourseStatus; retrainingPolicyId: number | null; occurrenceNumber: number | null
  assignedCount: number; notStartedCount: number; inProgressCount: number; completedCount: number; failedCount: number; expiredCount: number
  completionRate: number | null; exam: StatisticsExam | null
}
export type StatisticsDepartment = {
  departmentId: number; departmentName: string; parentDepartmentId: number | null; active: boolean; currentEmployeeCount: number
  assignedCount: number; completedCount: number; failedCount: number; expiredCount: number; completionRate: number | null
}
export type StatisticsCourses = { meta: StatisticsMeta; data: PageResponse<StatisticsCourse> }
export type StatisticsDepartments = { meta: StatisticsMeta; data: PageResponse<StatisticsDepartment> }
export type StatisticsTrendPoint = { bucketStart: string; periodStart: string; periodEnd: string; assignments: number; completions: number }
export type StatisticsTrends = { meta: StatisticsMeta; granularity: 'DAY' | 'MONTH'; points: StatisticsTrendPoint[] }
