import { apiClient } from './client.ts'
import type { Enrollment, EnrollmentPage, EnrollmentSearch } from './assignmentTypes.ts'

export const enrollmentApi = {
  async forCourse(courseId: number, params: EnrollmentSearch, signal?: AbortSignal) { return (await apiClient.get<EnrollmentPage>(`/courses/${courseId}/enrollments`, { params, signal })).data },
  async get(id: number, signal?: AbortSignal) { return (await apiClient.get<Enrollment>(`/enrollments/${id}`, { signal })).data },
  async assign(courseId: number, memberId: number) { return (await apiClient.post<Enrollment>(`/courses/${courseId}/enrollments`, { memberId })).data },
}
