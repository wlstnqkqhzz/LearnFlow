import { type AxiosInstance, isAxiosError } from 'axios';

export type EnrollmentStatus = 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'EXPIRED';
export type CourseType = 'MANDATORY' | 'OPTIONAL';
export type Enrollment = {
  enrollmentId: number; memberId: number; memberName: string; courseId: number;
  courseTitle: string; courseType: CourseType; courseStartDate: string; courseEndDate: string;
  status: EnrollmentStatus; assignmentSource: 'MANUAL' | 'AUTOMATIC'; assignmentRuleId: number | null;
  assignedAt: string; startedAt: string | null; completedAt: string | null; dueDate: string;
};
export type Content = {
  contentId: number; title: string; contentType: 'VIDEO' | 'DOCUMENT' | 'LINK';
  contentUrl: string; durationSeconds: number | null; required: boolean; sortOrder: number;
  progressRate: number; completedAt: string | null;
};
export type LearningDetail = {
  enrollmentId: number; status: EnrollmentStatus; dueDate: string; assignedAt: string;
  startedAt: string | null; completedAt: string | null; courseId: number; courseTitle: string;
  courseDescription: string | null; courseType: CourseType; courseStartDate: string; courseEndDate: string;
  instructorId: number | null; instructorName: string | null; progressRate: number;
  passingProgressRate: number; contentConditionSatisfied: boolean; contents: Content[];
  exam: { examId: number; title: string; passingScore: number; maxAttempts: number } | null;
};
export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
export type EnrollmentRow = Enrollment & { progressRate: number | null; progressUnavailable: boolean };
export const statusLabels: Record<EnrollmentStatus, string> = { ASSIGNED: '학습 전', IN_PROGRESS: '학습 중', COMPLETED: '수료', FAILED: '실패', EXPIRED: '만료' };
export const contentLabels = { VIDEO: '동영상', DOCUMENT: '문서', LINK: '외부 링크' };
export const editable = (status: EnrollmentStatus) => status === 'ASSIGNED' || status === 'IN_PROGRESS';
export const textValue = (value: string | null | undefined) => value?.trim() || '—';
export const rateValue = (value: number | null | undefined) => typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= 100 ? value : null;
export const rateLabel = (value: number | null | undefined) => rateValue(value) === null ? '—' : `${value}%`;
export function routeId(value: string | string[] | undefined) {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return null;
  const id = Number(value); return Number.isSafeInteger(id) ? id : null;
}
export function learningError(error: unknown) {
  if (isAxiosError(error)) {
    if (error.response?.status === 403) return '본인에게 배정된 교육만 이용할 수 있습니다.';
    if (error.response?.status === 404) return '교육 또는 콘텐츠를 찾을 수 없습니다.';
    if (error.response?.status === 401) return '인증을 확인할 수 없습니다. 다시 로그인해 주세요.';
    if (error.response?.status === 409 || error.response?.status === 400) return '교육 상태가 변경되었거나 저장할 수 없는 값입니다. 새로고침 후 확인해 주세요.';
    if (!error.response) return '서버에 연결할 수 없습니다. 연결을 확인하고 다시 시도해 주세요.';
  }
  return '요청을 완료하지 못했습니다. 다시 시도해 주세요.';
}
export function parseProgress(input: string) {
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(input.trim())) return null;
  return rateValue(Number(input.trim()));
}
export function safeContentUrl(value: string) {
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.href : null; } catch { return null; }
}
export async function openContentUrl(value: string, open: (url: string) => Promise<unknown>) {
  const url = safeContentUrl(value);
  if (!url) return '올바른 http/https 콘텐츠 주소가 아닙니다.';
  try { await open(url); return ''; } catch { return '콘텐츠를 열 수 없습니다. 기기의 브라우저 설정과 연결을 확인해 주세요.'; }
}
export function createLearningApi(client: AxiosInstance) {
  const detail = async (id: number, signal?: AbortSignal) => (await client.get<LearningDetail>(`/enrollments/${id}/progress`, { signal })).data;
  return {
    detail,
    async list(status?: EnrollmentStatus, page = 0, signal?: AbortSignal): Promise<Page<EnrollmentRow>> {
      const result = (await client.get<Page<Enrollment>>('/enrollments/me', { params: { status, page, size: 10 }, signal })).data;
      // The list DTO has no progress field. Bound enrichment to this page and three concurrent requests.
      const rows: EnrollmentRow[] = result.content.map(row => ({ ...row, progressRate: null, progressUnavailable: false }));
      let next = 0;
      await Promise.all(Array.from({ length: Math.min(3, rows.length) }, async () => {
        while (next < rows.length && !signal?.aborted) {
          const index = next++;
          try { const value = await detail(rows[index].enrollmentId, signal); rows[index] = { ...rows[index], status: value.status, progressRate: value.progressRate }; }
          catch (error) { if (signal?.aborted || (isAxiosError(error) && error.response?.status === 401)) throw error; rows[index].progressUnavailable = true; }
        }
      }));
      return { ...result, content: rows };
    },
    async save(id: number, contentId: number, progressRate: number, signal?: AbortSignal) {
      return (await client.patch<LearningDetail>(`/enrollments/${id}/contents/${contentId}/progress`, { progressRate }, { signal })).data;
    },
  };
}
