import { apiClient } from './client.ts'
import type { EnrollmentStatus } from './assignmentTypes.ts'
import type { CourseType } from './courseTypes.ts'

export type Dashboard = {
  overview: { employeeCount: number; openCourseCount: number; ongoingEnrollmentCount: number; completionRate: number }
  distribution: { total: number; counts: { status: EnrollmentStatus; count: number }[] }
  activeCourses: { courseId: number; title: string; type: CourseType; enrollmentCount: number; completedCount: number; completionRate: number }[]
  attention: { dueSoonCount: number; failedCount: number; expiredCount: number }
  recentCompletions: { enrollmentId: number; memberName: string; courseTitle: string; completedAt: string }[]
  recentAssignments: { enrollmentId: number; memberName: string; departmentName: string | null; courseTitle: string; status: EnrollmentStatus; assignedAt: string }[]
}
export const dashboardApi = {
  async get(signal?: AbortSignal) { return (await apiClient.get<Dashboard>('/admin/dashboard', { signal })).data },
}
