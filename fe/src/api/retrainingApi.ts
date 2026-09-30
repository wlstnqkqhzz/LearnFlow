import { apiClient } from './client.ts'
import type { Course, CoursePage } from './courseTypes.ts'
import type { RetrainingPolicy, RetrainingPolicyPage, RetrainingPolicyRequest } from './retrainingTypes.ts'

const path = '/retraining-policies'
export const retrainingApi = {
  async list(page = 0, size = 20, signal?: AbortSignal) {
    return (await apiClient.get<RetrainingPolicyPage>(path, { params: { page, size }, signal })).data
  },
  async get(id: number, signal?: AbortSignal) {
    return (await apiClient.get<RetrainingPolicy>(`${path}/${id}`, { signal })).data
  },
  async create(request: RetrainingPolicyRequest) {
    return (await apiClient.post<RetrainingPolicy>(path, request)).data
  },
  // Backend PATCH는 부분 수정이 아니라 필수 설정 전체를 받는다.
  async update(id: number, request: RetrainingPolicyRequest) {
    return (await apiClient.patch<RetrainingPolicy>(`${path}/${id}`, request)).data
  },
  async status(id: number, enabled: boolean) {
    return (await apiClient.patch<RetrainingPolicy>(`${path}/${id}/status`, { enabled })).data
  },
  async courses(id: number, page = 0, size = 20, signal?: AbortSignal) {
    return (await apiClient.get<CoursePage>(`${path}/${id}/courses`, { params: { page, size }, signal })).data
  },
  async generate(id: number, occurrenceNumber: number) {
    // 201(생성)과 200(기존 회차)을 모두 정상 Course 응답으로 처리한다.
    return (await apiClient.put<Course>(`${path}/${id}/occurrences/${occurrenceNumber}`)).data
  },
  async skip(id: number, occurrenceNumber: number) {
    return (await apiClient.post<RetrainingPolicy>(`${path}/${id}/skip-overdue`, { occurrenceNumber })).data
  },
}
