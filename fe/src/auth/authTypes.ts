export type Role = 'EMPLOYEE' | 'INSTRUCTOR' | 'ADMIN'

// 현재 Backend는 이름·재직 상태를 반환하지 않는다.
export type AuthUser = { memberId: number; email: string; roles: Role[] }
export type LoginRequest = { email: string; password: string }
export type TokenResponse = {
  accessToken: string
  refreshToken: string
  tokenType: 'Bearer'
  accessTokenExpiresInSeconds: number
  refreshTokenExpiresInSeconds: number
}
export type StoredTokens = {
  accessToken: string
  refreshToken: string
  accessExpiresAt: number
  refreshExpiresAt: number
}
