import { apiClient, publicClient } from './client.ts'
import type { LoginRequest, TokenResponse } from '../auth/authTypes.ts'

export const authApi = {
  async login(request: LoginRequest) {
    return (await publicClient.post<TokenResponse>('/auth/login', request)).data
  },
  async logout() { await apiClient.post('/auth/logout') },
}
