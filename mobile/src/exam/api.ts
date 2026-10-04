import { type AxiosInstance } from 'axios';
import type { LearningDetail } from '../learning/api';
export type Attempt = { attemptId: number; attemptNumber: number; score: number | null; passed: boolean | null; startedAt: string; submittedAt: string | null; maxAttempts: number; remainingAttempts: number };
export type Eligibility = { canStart: boolean; canContinue: boolean; passed: boolean; attemptCount: number; maxAttempts: number; remainingAttempts: number; openAttemptId: number | null; blockedReason: string | null };
export type Question = { questionId: number; questionType: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE'; questionText: string; score: number; sortOrder: number; choices: { choiceId: number; choiceText: string; sortOrder: number }[]; selectedChoiceIds: number[] };
export type Paper = { attemptId: number; title: string; questions: Question[] };
export type Answer = { questionId: number; selectedChoiceIds: number[] };
export const questionLabels = { SINGLE_CHOICE: '단일 선택', MULTIPLE_CHOICE: '복수 선택', TRUE_FALSE: '참 / 거짓' };
export function selectChoice(type: Question['questionType'], ids: number[], id: number) { return type === 'MULTIPLE_CHOICE' ? ids.includes(id) ? ids.filter(value => value !== id) : [...ids, id] : [id]; }
export const blockedLabels: Record<string, string> = { EXAM_NOT_FOUND: '등록된 시험이 없습니다.', ENROLLMENT_EXAM_NOT_EDITABLE: '종료된 교육에서는 응시할 수 없습니다.', EXAM_ALREADY_PASSED: '이미 합격한 시험입니다.', EXAM_ATTEMPTS_EXHAUSTED: '응시 기회를 모두 사용했습니다.', INVALID_EXAM_CONFIGURATION: '시험 구성이 완료되지 않았습니다.' };
export function createExamApi(client: AxiosInstance) {
  const history = async (id: number, signal?: AbortSignal) => (await client.get<Attempt[]>(`/enrollments/${id}/exam-attempts`, { signal })).data;
  const eligibility = async (id: number, signal?: AbortSignal) => (await client.get<Eligibility>(`/enrollments/${id}/exam-eligibility`, { signal })).data;
  const paper = async (id: number, signal?: AbortSignal) => (await client.get<Paper>(`/exam-attempts/${id}`, { signal })).data;
  const result = async (id: number, signal?: AbortSignal) => (await client.get<Attempt>(`/exam-attempts/${id}/result`, { signal })).data;
  return {
    history, eligibility, paper, result,
    async summary(id: number, signal?: AbortSignal) { const [access, attempts] = await Promise.all([eligibility(id, signal), history(id, signal)]); return { access, attempts }; },
    async start(id: number, signal?: AbortSignal) { return (await client.post<Attempt>(`/enrollments/${id}/exam-attempts`, undefined, { signal })).data; },
    async save(id: number, questionId: number, selectedChoiceIds: number[]) { return (await client.put<Answer>(`/exam-attempts/${id}/answers/${questionId}`, { selectedChoiceIds })).data; },
    async submit(id: number) { return (await client.post<Attempt>(`/exam-attempts/${id}/submit`)).data; },
    async load(enrollmentId: number, attemptId: number, signal?: AbortSignal) {
      const [attempts, access, detail] = await Promise.all([history(enrollmentId, signal), eligibility(enrollmentId, signal), client.get<LearningDetail>(`/enrollments/${enrollmentId}/progress`, { signal }).then(r => r.data)]);
      const attempt = attempts.find(a => a.attemptId === attemptId);
      if (!attempt) throw new Error('이 교육에 해당하는 응시를 찾을 수 없습니다.');
      return { detail, access, attempt: attempt.submittedAt ? await result(attemptId, signal) : attempt,
        paper: !attempt.submittedAt && access.canContinue && access.openAttemptId === attemptId ? await paper(attemptId, signal) : null };
    },
  };
}

// Starting an attempt may finish after the employee leaves the detail screen.
export function createAttemptStart(start: (id: number, signal: AbortSignal) => Promise<Attempt>) {
  let generation = 0;
  let active: { controller: AbortController; promise: Promise<void> } | null = null;
  return {
    cancel() { generation++; active?.controller.abort(); active = null; },
    run(id: number, navigate: (attempt: Attempt) => void) {
      if (active) return active.promise;
      const ticket = generation, controller = new AbortController();
      const promise = start(id, controller.signal).then(attempt => {
        if (ticket === generation && !controller.signal.aborted) navigate(attempt);
      }, error => { if (ticket === generation && !controller.signal.aborted) throw error; })
        .finally(() => { if (active?.controller === controller) active = null; });
      active = { controller, promise }; return promise;
    },
  };
}
