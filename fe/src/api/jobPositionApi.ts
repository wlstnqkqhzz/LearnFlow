import { apiClient } from './client.ts'
import type { JobPosition } from './adminTypes.ts'

export const jobPositionApi = {
  async list(active = false, signal?: AbortSignal) { return (await apiClient.get<JobPosition[]>('/job-positions', { params: { active }, signal })).data },
  async get(id: number, signal?: AbortSignal) { return (await apiClient.get<JobPosition>(`/job-positions/${id}`, { signal })).data },
  async create(data: { code: string; name: string }) { return (await apiClient.post<JobPosition>('/job-positions', data)).data },
  async update(id: number, data: { name: string }) { return (await apiClient.patch<JobPosition>(`/job-positions/${id}`, data)).data },
  async status(id: number, active: boolean) { return (await apiClient.patch<JobPosition>(`/job-positions/${id}/status`, { active })).data },
}
