import type { Role } from '../auth/authTypes.ts'

export type Department = { id: number; code: string; name: string; parentDepartmentId: number | null; isActive: boolean; createdAt: string; updatedAt: string }
export type JobPosition = { id: number; code: string; name: string; isActive: boolean; createdAt: string; updatedAt: string }
export type MemberStatus = 'ACTIVE' | 'ON_LEAVE' | 'RESIGNED'
export type Member = {
  id: number; employeeNumber: string; email: string; name: string
  departmentId: number; jobPositionId: number; status: MemberStatus
  hireDate: string; resignedAt: string | null; roles: Role[]; createdAt: string; updatedAt: string
}
export type MemberCreateRequest = Pick<Member, 'employeeNumber' | 'email' | 'name' | 'departmentId' | 'jobPositionId' | 'hireDate'> & { password: string }
export type MemberPatchRequest = Partial<Pick<Member, 'email' | 'name' | 'hireDate'>>
export type MemberSearch = { page: number; size: number; name?: string; status?: MemberStatus; departmentId?: number; jobPositionId?: number }
export type PageResponse<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
