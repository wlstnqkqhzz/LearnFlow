import { apiClient } from './client.ts'
import type { PageResponse } from './adminTypes.ts'

export type NotificationType = 'ENROLLMENT_ASSIGNED' | 'COURSE_COMPLETED' | 'COURSE_FAILED' | 'ENROLLMENT_EXPIRED'
export type Notification = {
  notificationId: number
  type: NotificationType
  title: string
  message: string
  relatedEnrollmentId: number | null
  readAt: string | null
  createdAt: string
}
export const notificationApi = {
  async list(page = 0, size = 20) {
    return (await apiClient.get<PageResponse<Notification>>('/notifications/me', { params: { page, size } })).data
  },
  async unread() { return (await apiClient.get<{ count: number }>('/notifications/me/unread-count')).data },
  async read(id: number) { return (await apiClient.patch<Notification>(`/notifications/${id}/read`)).data },
  async readAll() { return (await apiClient.patch<{ readAt: string }>('/notifications/me/read-all')).data },
}
