import { apiClient } from './client.ts'
import type { Member, MemberCreateRequest, MemberPatchRequest, MemberSearch, MemberStatus, PageResponse } from './adminTypes.ts'
import type { Role } from '../auth/authTypes.ts'

export const memberApi = {
  async list(params: MemberSearch, signal?: AbortSignal) { return (await apiClient.get<PageResponse<Member>>('/members', { params, signal })).data },
  async get(id: number, signal?: AbortSignal) { return (await apiClient.get<Member>(`/members/${id}`, { signal })).data },
  async create(data: MemberCreateRequest) { return (await apiClient.post<Member>('/members', data)).data },
  async update(id: number, data: MemberPatchRequest) { return (await apiClient.patch<Member>(`/members/${id}`, data)).data },
  async department(id: number, departmentId: number) { return (await apiClient.patch<Member>(`/members/${id}/department`, { departmentId })).data },
  async jobPosition(id: number, jobPositionId: number) { return (await apiClient.patch<Member>(`/members/${id}/job-position`, { jobPositionId })).data },
  async status(id: number, status: MemberStatus) { return (await apiClient.patch<Member>(`/members/${id}/status`, { status })).data },
  async addRole(id: number, role: Exclude<Role, 'EMPLOYEE'>) { return (await apiClient.post<Member>(`/members/${id}/roles/${role}`)).data },
  async removeRole(id: number, role: Exclude<Role, 'EMPLOYEE'>) { return (await apiClient.delete<Member>(`/members/${id}/roles/${role}`)).data },
}
