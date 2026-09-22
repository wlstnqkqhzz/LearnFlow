import { apiClient } from './client.ts'
import type { Course, CourseCreateRequest, CoursePage, CoursePatchRequest, CourseSearch, CourseStatus } from './courseTypes.ts'

export const courseApi = {
  async list(params: CourseSearch, signal?: AbortSignal) {
    return (await apiClient.get<CoursePage>('/courses', { params, signal })).data
  },
  async get(id: number, signal?: AbortSignal) {
    return (await apiClient.get<Course>(`/courses/${id}`, { signal })).data
  },
  async create(data: CourseCreateRequest) {
    return (await apiClient.post<Course>('/courses', data)).data
  },
  async update(id: number, data: CoursePatchRequest) {
    return (await apiClient.patch<Course>(`/courses/${id}`, data)).data
  },
  async status(id: number, status: Exclude<CourseStatus, 'DRAFT'>) {
    return (await apiClient.patch<Course>(`/courses/${id}/status`, { status })).data
  },
}
