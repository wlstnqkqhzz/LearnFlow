import { apiClient } from './client.ts'
import type { MyEnrollmentPage, MyEnrollmentSearch, MyLearningDetail } from './learningTypes.ts'

export const employeeLearningApi = {
  async list(params: MyEnrollmentSearch, signal?: AbortSignal) {
    return (await apiClient.get<MyEnrollmentPage>('/enrollments/me', { params, signal })).data
  },
  async detail(enrollmentId: number, signal?: AbortSignal) {
    return (await apiClient.get<MyLearningDetail>(`/enrollments/${enrollmentId}/progress`, { signal })).data
  },
  async completeContent(enrollmentId: number, contentId: number) {
    return (await apiClient.patch<MyLearningDetail>(`/enrollments/${enrollmentId}/contents/${contentId}/progress`, { progressRate: 100 })).data
  },
}
