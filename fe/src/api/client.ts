import axios from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'
import { authSession } from '../auth/authSession.ts'
import type { TokenResponse } from '../auth/authTypes.ts'

const baseURL = import.meta.env?.VITE_API_BASE_URL || '/api'
const options = { baseURL, timeout: 15_000 }
// 로그인/재발급에는 인증 인터셉터를 적용하지 않는다.
export const publicClient = axios.create(options)
export const apiClient = axios.create(options)
type AuthRequest = InternalAxiosRequestConfig & { _retry?: boolean; _generation?: number; _revision?: number }
let refreshFlight: { generation: number; promise: Promise<void> } | null = null
const forbiddenListeners = new Set<() => void>()
export function onForbidden(listener: () => void) {
  forbiddenListeners.add(listener)
  return () => { forbiddenListeners.delete(listener) }
}

export function refreshAccessToken(): Promise<void> {
  const generation = authSession.getGeneration()
  if (refreshFlight?.generation === generation) return refreshFlight.promise
  const promise = (async () => {
    try {
      const tokens = authSession.getTokens()
      if (!tokens || tokens.refreshExpiresAt <= Date.now()) throw new Error('다시 로그인해 주세요.')
      const { data } = await publicClient.post<TokenResponse>('/auth/refresh', { refreshToken: tokens.refreshToken })
      authSession.accept(data, generation)
    } catch (error) {
      if (generation === authSession.getGeneration()) authSession.clear()
      throw error
    }
  })()
  const flight = { generation, promise }
  refreshFlight = flight
  // finally에서 만들어지는 거부 Promise를 남기지 않는다.
  void promise.then(() => { if (refreshFlight === flight) refreshFlight = null },
    () => { if (refreshFlight === flight) refreshFlight = null })
  return promise
}

apiClient.interceptors.request.use(config => {
  const request = config as AuthRequest
  if (request._generation !== undefined && request._generation !== authSession.getGeneration()) {
    throw new axios.CanceledError('인증 상태가 변경되었습니다.')
  }
  request._generation = authSession.getGeneration()
  request._revision = authSession.getRevision()
  const token = authSession.getTokens()?.accessToken
  if (token) request.headers.set('Authorization', `Bearer ${token}`)
  else request.headers.delete('Authorization')
  return request
})

apiClient.interceptors.response.use(response => {
  if ((response.config as AuthRequest)._generation !== authSession.getGeneration()) {
    throw new axios.CanceledError('인증 상태가 변경되었습니다.')
  }
  return response
}, async (error: unknown) => {
  if (!axios.isAxiosError(error) || !error.config) return Promise.reject(error)
  const request = error.config as AuthRequest
  if (request._generation !== authSession.getGeneration()) return Promise.reject(error)
  if (error.response?.status === 403) {
    forbiddenListeners.forEach(listener => listener())
    return Promise.reject(error)
  }
  if (error.response?.status !== 401) return Promise.reject(error)
  if (request._retry) {
    if (request._revision === authSession.getRevision()) authSession.clear()
    return Promise.reject(error)
  }
  request._retry = true
  // 뒤늦게 도착한 이전 요청의 401에는 이미 회전된 토큰을 사용한다.
  // Access Token 문자열이 같아도 재발급 응답 revision으로 구분한다.
  if (request._revision === authSession.getRevision()) await refreshAccessToken()
  if (!authSession.getTokens() || request._generation !== authSession.getGeneration()) return Promise.reject(error)
  return apiClient.request(request)
})
