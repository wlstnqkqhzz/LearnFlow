import { isAxiosError } from 'axios';
import type { createNotificationApi } from './api';

export function notificationId(value: unknown): number | null {
  if (typeof value !== 'number' && (typeof value !== 'string' || !/^[1-9]\d{0,15}$/.test(value))) return null;
  const id = Number(value); return Number.isSafeInteger(id) && id > 0 ? id : null;
}
export const unavailable = (error: unknown) => isAxiosError(error) && [403, 404].includes(error.response?.status ?? 0);
export async function resolveNotification(api: ReturnType<typeof createNotificationApi>, id: number, signal: AbortSignal) {
  let notification = await api.get(id, signal);
  if (signal.aborted) throw new Error('cancelled');
  if (notification.readAt === null) notification = await api.read(id, signal);
  if (signal.aborted) throw new Error('cancelled');
  const related = notificationId(notification.relatedEnrollmentId);
  if (related === null) return { notification, destination: null, message: '연결된 교육이 없습니다. 알림 내용은 여기에서 확인할 수 있습니다.', retryable: false };
  try { await api.checkEnrollment(related, signal); }
  catch (error) { return { notification, destination: null, message: '관련 교육에 접근할 수 없습니다. 알림 내용은 여기에서 확인할 수 있습니다.', retryable: !unavailable(error) }; }
  return { notification, destination: related, message: '', retryable: false };
}
