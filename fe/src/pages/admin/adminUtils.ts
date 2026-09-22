import type { Department, MemberStatus } from '../../api/adminTypes.ts'
import type { Role } from '../../auth/authTypes.ts'

export const memberStatuses: Record<MemberStatus, { label: string; tone: string }> = {
  ACTIVE: { label: '재직', tone: 'success' }, ON_LEAVE: { label: '휴직', tone: 'warning' }, RESIGNED: { label: '퇴사', tone: 'neutral' },
}
export const roleLabels: Record<Role, string> = { EMPLOYEE: '직원', INSTRUCTOR: '강사', ADMIN: '관리자' }
export function allowedStatuses(status: MemberStatus): MemberStatus[] {
  return status === 'RESIGNED' ? [] : status === 'ACTIVE' ? ['ON_LEAVE', 'RESIGNED'] : ['ACTIVE', 'RESIGNED']
}
export function organizationName(items: { id: number; name: string; isActive: boolean }[], id: number) {
  const item = items.find(value => value.id === id)
  return item ? `${item.name}${item.isActive ? '' : ' (비활성)'}` : `미확인 항목 #${id}`
}
export function selectableOrganizations<T extends { id: number; isActive: boolean }>(items: T[], currentId?: number): T[] {
  return items.filter(item => item.isActive || item.id === currentId)
}

// 평면 응답을 순서/깊이로만 변환한다. 순환 데이터가 들어와도 무한 순회하지 않는다.
export function departmentRows(items: Department[]) {
  const rows: { department: Department; depth: number }[] = []
  const visited = new Set<number>()
  const ids = new Set(items.map(item => item.id))
  const children = new Map<number | null, Department[]>()
  for (const item of items) {
    const parent = item.parentDepartmentId !== null && ids.has(item.parentDepartmentId) ? item.parentDepartmentId : null
    children.set(parent, [...(children.get(parent) ?? []), item])
  }
  function walk(start: Department, depth: number) {
    const stack = [{ department: start, depth }]
    while (stack.length) {
      const row = stack.pop()!
      if (visited.has(row.department.id)) continue
      visited.add(row.department.id)
      rows.push(row)
      for (const child of [...(children.get(row.department.id) ?? [])].reverse()) stack.push({ department: child, depth: row.depth + 1 })
    }
  }
  for (const root of children.get(null) ?? []) walk(root, 0)
  for (const item of items) if (!visited.has(item.id)) walk(item, 0)
  return rows
}
export function parentCandidates(items: Department[], editing?: Department) {
  if (!editing) return items.filter(item => item.isActive)
  const excluded = new Set([editing.id])
  let added = true
  while (added) {
    added = false
    for (const item of items) {
      if (item.parentDepartmentId !== null && excluded.has(item.parentDepartmentId) && !excluded.has(item.id)) { excluded.add(item.id); added = true }
    }
  }
  return items.filter(item => !excluded.has(item.id) && (item.isActive || item.id === editing.parentDepartmentId))
}
