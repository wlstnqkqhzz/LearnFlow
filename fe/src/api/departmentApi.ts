import { apiClient } from './client.ts'
import type { Department } from './adminTypes.ts'

export type DepartmentCreateRequest = { code: string; name: string; parentDepartmentId: number | null }
export type DepartmentPatchRequest = { name?: string; parentDepartmentId?: number | null }
export const departmentApi = {
  async list(active = false, signal?: AbortSignal) { return (await apiClient.get<Department[]>('/departments', { params: { active }, signal })).data },
  async get(id: number, signal?: AbortSignal) { return (await apiClient.get<Department>(`/departments/${id}`, { signal })).data },
  async create(data: DepartmentCreateRequest) { return (await apiClient.post<Department>('/departments', data)).data },
  async update(id: number, data: DepartmentPatchRequest) { return (await apiClient.patch<Department>(`/departments/${id}`, data)).data },
  async status(id: number, active: boolean) { return (await apiClient.patch<Department>(`/departments/${id}/status`, { active })).data },
}
