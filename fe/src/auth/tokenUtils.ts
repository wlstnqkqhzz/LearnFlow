import type { AuthUser, Role } from './authTypes.ts'

// 서명 검증이 아닌 UI용 파싱이다. 실제 인증·권한 판단은 Backend에서 수행한다.
export function readAccessToken(token: string): { user: AuthUser; expiresAt: number } {
  const parts = token.split('.')
  if (parts.length !== 3 || !parts.every(Boolean)) throw new Error('인증 응답을 확인할 수 없습니다.')
  const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
  const bytes = Uint8Array.from(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')), c => c.charCodeAt(0))
  const payload = JSON.parse(new TextDecoder().decode(bytes))
  const roles: Role[] = ['EMPLOYEE', 'INSTRUCTOR', 'ADMIN']
  if (!payload || payload.tokenType !== 'ACCESS' || !Number.isSafeInteger(payload.memberId)
    || payload.memberId <= 0 || typeof payload.email !== 'string' || !payload.email.trim()
    || !Array.isArray(payload.roles) || !payload.roles.includes('EMPLOYEE')
    || !payload.roles.every((role: Role) => roles.includes(role))
    || !Number.isFinite(payload.exp) || payload.exp <= 0) {
    throw new Error('인증 응답을 확인할 수 없습니다.')
  }
  return { user: { memberId: payload.memberId, email: payload.email, roles: [...new Set<Role>(payload.roles)] }, expiresAt: payload.exp * 1000 }
}

export function homePath(user: AuthUser) {
  if (user.roles.includes('ADMIN')) return '/admin/dashboard'
  if (user.roles.includes('EMPLOYEE')) return '/employee'
  if (user.roles.includes('INSTRUCTOR')) return '/instructor'
  return '/403'
}

export function userLabel(user: AuthUser | null) {
  if (user?.roles.includes('ADMIN')) return '관리자'
  if (user?.roles.includes('INSTRUCTOR')) return '강사'
  return '직원'
}
