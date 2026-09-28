import { apiClient } from './client.ts'
import type { Assignment, AssignmentRequest, EmployeeAssignment, GradeRequest, Submission, SubmissionPage, SubmissionRequest, SubmissionSearch } from './courseworkTypes.ts'

export const courseworkApi = {
  async list(courseId: number, signal?: AbortSignal) {
    return (await apiClient.get<Assignment[]>(`/courses/${courseId}/assignments`, { signal })).data
  },
  async get(courseId: number, assignmentId: number, signal?: AbortSignal) {
    return (await apiClient.get<Assignment>(`/courses/${courseId}/assignments/${assignmentId}`, { signal })).data
  },
  async create(courseId: number, request: AssignmentRequest) {
    return (await apiClient.post<Assignment>(`/courses/${courseId}/assignments`, request)).data
  },
  async update(courseId: number, assignmentId: number, request: Partial<AssignmentRequest>) {
    return (await apiClient.patch<Assignment>(`/courses/${courseId}/assignments/${assignmentId}`, request)).data
  },
  async remove(courseId: number, assignmentId: number) {
    await apiClient.delete(`/courses/${courseId}/assignments/${assignmentId}`)
  },
  async reorder(courseId: number, assignmentIds: number[]) {
    return (await apiClient.patch<Assignment[]>(`/courses/${courseId}/assignments/order`, { assignmentIds })).data
  },
  async submissions(courseId: number, params: SubmissionSearch, signal?: AbortSignal) {
    return (await apiClient.get<SubmissionPage>(`/courses/${courseId}/assignment-submissions`, { params, signal })).data
  },
  async submission(submissionId: number, signal?: AbortSignal) {
    return (await apiClient.get<Submission>(`/assignment-submissions/${submissionId}`, { signal })).data
  },
  async grade(submissionId: number, request: GradeRequest) {
    return (await apiClient.patch<Submission>(`/assignment-submissions/${submissionId}/grade`, { score: request.score, feedback: request.feedback })).data
  },
  async mine(enrollmentId: number, signal?: AbortSignal) {
    return (await apiClient.get<EmployeeAssignment[]>(`/enrollments/${enrollmentId}/assignments`, { signal })).data
  },
  async mineOne(enrollmentId: number, assignmentId: number, signal?: AbortSignal) {
    return (await apiClient.get<EmployeeAssignment>(`/enrollments/${enrollmentId}/assignments/${assignmentId}`, { signal })).data
  },
  async submit(enrollmentId: number, assignmentId: number, request: SubmissionRequest) {
    return (await apiClient.put<EmployeeAssignment>(`/enrollments/${enrollmentId}/assignments/${assignmentId}/submission`,
      { submissionType: request.submissionType, content: request.content })).data
  },
}
