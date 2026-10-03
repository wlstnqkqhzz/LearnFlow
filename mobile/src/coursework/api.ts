import { type AxiosInstance, isAxiosError } from 'axios';
import type { LearningDetail } from '../learning/api';
export type SubmissionType = 'TEXT' | 'URL';
export type AssignmentStatus = 'NOT_SUBMITTED' | 'PENDING_GRADING' | 'PASSED' | 'FAILED';
export type Assignment = { assignmentId: number; courseId: number; title: string; description: string | null; required: boolean; dueDate: string; passingScore: number; sortOrder: number; createdAt: string; updatedAt: string };
export type Submission = { submissionId: number; assignmentId: number; enrollmentId: number; memberId: number; memberName: string; submissionType: SubmissionType; content: string; submittedAt: string; submissionCount: number; status: AssignmentStatus; score: number | null; passed: boolean | null; feedback: string | null; gradedAt: string | null; gradedByMemberId: number | null; gradedByName: string | null; version: number };
export type EmployeeAssignment = { assignment: Assignment; status: AssignmentStatus; effectiveDueDate: string; submittable: boolean; resubmittable: boolean; submission: Submission | null };
export const statusLabels: Record<AssignmentStatus, string> = { NOT_SUBMITTED: '미제출', PENDING_GRADING: '채점 대기', PASSED: '통과', FAILED: '미통과' };
export const maySubmit = (value: EmployeeAssignment) => value.submission ? value.resubmittable : value.submittable;
export const contentLimit = (type: SubmissionType) => type === 'URL' ? 2048 : 10000;
export function validateSubmission(type: SubmissionType, content: string) {
  if (!content.trim()) return '제출 내용을 입력해 주세요.';
  return content.length > contentLimit(type) ? `최대 ${contentLimit(type).toLocaleString()}자까지 입력할 수 있습니다.` : '';
}
export function courseworkError(error: unknown) {
  const labels: Record<string, string> = { ASSIGNMENT_DEADLINE_PASSED: '제출 마감일이 지났습니다.', ASSIGNMENT_SUBMISSION_NOT_EDITABLE: '현재 제출을 수정할 수 없습니다.', ASSIGNMENT_ALREADY_PASSED: '이미 통과한 과제입니다.', INVALID_ASSIGNMENT_CONTENT: '제출 내용을 확인해 주세요. URL은 유효한 http/https 주소여야 합니다.' };
  if (isAxiosError<{ code?: string }>(error)) {
    if (error.response?.data?.code && labels[error.response.data.code]) return labels[error.response.data.code];
    if (error.response?.status === 403) return '본인에게 배정된 과제만 이용할 수 있습니다.';
  }
  return '제출 결과를 확인하지 못했습니다. 서버 상태를 다시 조회합니다. 제출 횟수와 내용을 확인해 주세요.';
}
export function gradeTime(value: string | null) {
  if (!value) return '—';
  const date = new Date(/(?:Z|[+-]\d\d:\d\d)$/.test(value) ? value : `${value}Z`);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' });
}
export function createCourseworkApi(client: AxiosInstance) {
  const detail = async (id: number, assignmentId: number, signal?: AbortSignal) => (await client.get<EmployeeAssignment>(`/enrollments/${id}/assignments/${assignmentId}`, { signal })).data;
  return {
    detail,
    async list(id: number, signal?: AbortSignal) { return (await client.get<EmployeeAssignment[]>(`/enrollments/${id}/assignments`, { signal })).data; },
    async load(id: number, assignmentId: number, signal?: AbortSignal) {
      const [item, enrollment] = await Promise.all([detail(id, assignmentId, signal), client.get<LearningDetail>(`/enrollments/${id}/progress`, { signal }).then(r => r.data)]);
      return { item, enrollment };
    },
    async submit(id: number, assignmentId: number, submissionType: SubmissionType, content: string, signal?: AbortSignal) {
      return (await client.put<EmployeeAssignment>(`/enrollments/${id}/assignments/${assignmentId}/submission`, { submissionType, content }, { signal })).data;
    },
  };
}
export async function submitAndReload(api: ReturnType<typeof createCourseworkApi>, id: number, assignmentId: number, type: SubmissionType, content: string, signal?: AbortSignal) {
  let notice = '제출했습니다. 최신 제출 상태를 확인해 주세요.';
  try { await api.submit(id, assignmentId, type, content, signal); }
  catch (error) { notice = courseworkError(error); }
  // A lost response may already have committed. Reconcile with GET, never replay PUT.
  return { ...await api.load(id, assignmentId, signal), notice };
}
