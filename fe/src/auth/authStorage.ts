import type { StoredTokens } from './authTypes.ts'

const STORAGE_KEY = 'learnflow.auth'

// 저장소 접근을 중앙화한다. 암호·사용자 프로필은 저장하지 않는다.
export const authStorage = {
  read(): StoredTokens | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY)
      if (!raw) return null
      const value = JSON.parse(raw)
      if (typeof value?.accessToken !== 'string' || !value.accessToken
        || typeof value.refreshToken !== 'string' || !value.refreshToken
        || !Number.isFinite(value.accessExpiresAt) || !Number.isFinite(value.refreshExpiresAt)) {
        this.clear()
        return null
      }
      return value
    } catch { this.clear(); return null }
  },
  write(tokens: StoredTokens) {
    try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(tokens)) }
    catch { throw new Error('브라우저의 세션 저장소를 사용할 수 없습니다.') }
  },
  clear() {
    try { sessionStorage.removeItem(STORAGE_KEY) } catch { /* 메모리 인증은 별도로 항상 정리한다. */ }
  },
}
