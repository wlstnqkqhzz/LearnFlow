import { apiClient } from './client.ts'
import type { CourseContent, CourseContentCreateRequest, CourseContentPatchRequest } from './courseTypes.ts'

export const courseContentApi = {
  async list(courseId: number, signal?: AbortSignal) {
    return (await apiClient.get<CourseContent[]>(`/courses/${courseId}/contents`, { signal })).data
  },
  async create(courseId: number, data: CourseContentCreateRequest) {
    return (await apiClient.post<CourseContent>(`/courses/${courseId}/contents`, data)).data
  },
  async update(courseId: number, contentId: number, data: CourseContentPatchRequest) {
    return (await apiClient.patch<CourseContent>(`/courses/${courseId}/contents/${contentId}`, data)).data
  },
  async delete(courseId: number, contentId: number) {
    await apiClient.delete(`/courses/${courseId}/contents/${contentId}`)
  },
  async reorder(courseId: number, contentIds: number[]) {
    return (await apiClient.patch<CourseContent[]>(`/courses/${courseId}/contents/order`, { contentIds })).data
  },
}
