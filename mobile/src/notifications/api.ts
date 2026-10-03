import { type AxiosInstance } from 'axios';
export type Notification = { notificationId: number; type: string; title: string; message: string; relatedEnrollmentId: number | null; readAt: string | null; createdAt: string };
export type NotificationPage = { content: Notification[]; page: number; size: number; totalElements: number; totalPages: number };
export function notificationLabel(type: string) {
  const labels: Record<string, string> = { ENROLLMENT_ASSIGNED: '교육 배정', COURSE_COMPLETED: '교육 수료', COURSE_FAILED: '교육 실패', ENROLLMENT_EXPIRED: '교육 만료' };
  return Object.hasOwn(labels, type) ? labels[type] : '알림';
}
export function localTime(value: string) {
  if (!value) return '—';
  const date = new Date(/(?:Z|[+-]\d\d:\d\d)$/.test(value) ? value : `${value}Z`);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('ko-KR');
}
export const badgeLabel = (count: number | null) => count !== null && Number.isSafeInteger(count) && count > 0 ? count > 99 ? '99+' : String(count) : undefined;
export function foregroundHandler(refresh: () => void, initial: string) {
  let previous = initial;
  return (next: string) => { const returned = previous !== 'active' && next === 'active'; previous = next; if (returned) refresh(); };
}
export function createNotificationApi(client: AxiosInstance) {
  return {
    async list(page: number, signal?: AbortSignal) { return (await client.get<NotificationPage>('/notifications/me', { params: { page, size: 20 }, signal })).data; },
    async count(signal?: AbortSignal) { return (await client.get<{ count: number }>('/notifications/me/unread-count', { signal })).data.count; },
    async get(id: number, signal?: AbortSignal) { return (await client.get<Notification>(`/notifications/${id}`, { signal })).data; },
    async read(id: number, signal?: AbortSignal) { return (await client.patch<Notification>(`/notifications/${id}/read`, undefined, { signal })).data; },
    async readAll(signal?: AbortSignal) { return (await client.patch<{ readAt: string }>('/notifications/me/read-all', undefined, { signal })).data; },
    async checkEnrollment(id: number, signal?: AbortSignal) { await client.get(`/enrollments/${id}/progress`, { signal }); },
  };
}
