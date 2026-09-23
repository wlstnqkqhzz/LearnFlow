import { apiClient } from './client.ts'
import { employeeLearningApi } from './employeeLearningApi.ts'
import type { EmployeeExamAnswer, EmployeeExamAttempt, EmployeeExamPaper } from './employeeExamTypes.ts'

export const employeeExamApi = {
  async history(enrollmentId: number, signal?: AbortSignal) {
    return (await apiClient.get<EmployeeExamAttempt[]>(`/enrollments/${enrollmentId}/exam-attempts`, { signal })).data
  },
  async start(enrollmentId: number) {
    return (await apiClient.post<EmployeeExamAttempt>(`/enrollments/${enrollmentId}/exam-attempts`)).data
  },
  async paper(attemptId: number, signal?: AbortSignal) {
    return (await apiClient.get<EmployeeExamPaper>(`/exam-attempts/${attemptId}`, { signal })).data
  },
  async saveAnswer(attemptId: number, questionId: number, selectedChoiceIds: number[]) {
    return (await apiClient.put<EmployeeExamAnswer>(`/exam-attempts/${attemptId}/answers/${questionId}`, { selectedChoiceIds })).data
  },
  async submit(attemptId: number) {
    return (await apiClient.post<EmployeeExamAttempt>(`/exam-attempts/${attemptId}/submit`)).data
  },
  async result(attemptId: number, signal?: AbortSignal) {
    return (await apiClient.get<EmployeeExamAttempt>(`/exam-attempts/${attemptId}/result`, { signal })).data
  },
}

// 이력 소속 확인 후에만 문제를 조회한다. 종료 상태는 문제 편집 화면을 열지 않는다.
export async function loadEmployeeAttempt(enrollmentId: number, attemptId: number, signal?: AbortSignal) {
  const [detail, history] = await Promise.all([employeeLearningApi.detail(enrollmentId, signal), employeeExamApi.history(enrollmentId, signal)])
  const attempt = history.find(item => item.attemptId === attemptId)
  if (!attempt) return { detail, attempt: null, paper: null }
  const editable = detail.status === 'ASSIGNED' || detail.status === 'IN_PROGRESS'
  const paper = !attempt.submittedAt && editable ? await employeeExamApi.paper(attemptId, signal) : null
  return { detail, attempt, paper }
}
