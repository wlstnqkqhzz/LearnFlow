import { authApi } from '../api/authApi.ts'
import { refreshAccessToken } from '../api/client.ts'
import { authSession } from './authSession.ts'
import type { LoginRequest } from './authTypes.ts'

let initialization: Promise<void> | null = null
let logoutFlight: Promise<void> | null = null

// StrictMode의 effect 재실행에도 재발급은 한 번만 수행한다.
export function initializeAuth() {
  if (!initialization) {
    initialization = (async () => {
      authSession.load()
      if (authSession.getTokens()) {
        // /me가 없으므로 재발급으로 서버 검증과 최신 역할 조회를 함께 수행한다.
        try { await refreshAccessToken() } catch { /* refresh에서 인증 상태를 정리한다. */ }
      }
      authSession.finishInitialization()
    })()
  }
  return initialization
}

export async function login(request: LoginRequest) {
  if (logoutFlight) await logoutFlight
  authSession.clear()
  const generation = authSession.getGeneration()
  const response = await authApi.login({ email: request.email.trim(), password: request.password })
  return authSession.accept(response, generation)
}

export function logout() {
  if (logoutFlight) return logoutFlight
  const generation = authSession.getGeneration()
  logoutFlight = (async () => {
    try {
      if (authSession.getTokens()) await authApi.logout()
    } catch { /* 네트워크/서버 오류에도 사용자가 요청한 로컬 로그아웃을 완료한다. */ }
    finally { if (generation === authSession.getGeneration()) authSession.clear() }
  })()
  void logoutFlight.then(() => { logoutFlight = null })
  return logoutFlight
}
