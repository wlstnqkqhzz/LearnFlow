import { authStorage } from './authStorage.ts'
import { readAccessToken } from './tokenUtils.ts'
import type { AuthUser, StoredTokens, TokenResponse } from './authTypes.ts'

type Snapshot = { user: AuthUser | null; isInitializing: boolean }
let snapshot: Snapshot = { user: null, isInitializing: true }
let tokens: StoredTokens | null = null
let generation = 0
let revision = 0
const listeners = new Set<() => void>()

function publish(next: Snapshot) {
  snapshot = next
  listeners.forEach(listener => listener())
}

export const authSession = {
  subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener) } },
  getSnapshot: () => snapshot,
  getTokens: () => tokens,
  getGeneration: () => generation,
  getRevision: () => revision,
  load() { tokens = authStorage.read() },
  finishInitialization() { publish({ ...snapshot, isInitializing: false }) },
  clear() {
    generation++
    tokens = null
    authStorage.clear()
    publish({ user: null, isInitializing: false })
  },
  accept(response: TokenResponse, expectedGeneration: number) {
    // 로그아웃/다른 로그인 이후 도착한 응답은 저장하지 않는다.
    if (generation !== expectedGeneration) throw new Error('인증 요청이 취소되었습니다.')
    if (response?.tokenType !== 'Bearer' || typeof response.refreshToken !== 'string' || !response.refreshToken
      || typeof response.accessToken !== 'string'
      || !Number.isFinite(response.accessTokenExpiresInSeconds) || response.accessTokenExpiresInSeconds <= 0
      || !Number.isFinite(response.refreshTokenExpiresInSeconds) || response.refreshTokenExpiresInSeconds <= 0) {
      throw new Error('인증 응답을 확인할 수 없습니다.')
    }
    const { user, expiresAt } = readAccessToken(response.accessToken)
    if (expiresAt <= Date.now()) throw new Error('만료된 인증 응답입니다.')
    const next = {
      accessToken: response.accessToken, refreshToken: response.refreshToken,
      accessExpiresAt: Math.min(expiresAt, Date.now() + response.accessTokenExpiresInSeconds * 1000),
      refreshExpiresAt: Date.now() + response.refreshTokenExpiresInSeconds * 1000,
    }
    // 두 토큰을 하나의 저장 값으로 원자적으로 교체한다.
    authStorage.write(next)
    tokens = next
    revision++
    publish({ user, isInitializing: false })
    return user
  },
}
