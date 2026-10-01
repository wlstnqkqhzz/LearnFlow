import { apiClient } from './client.ts'

export type PushConfig = { enabled: boolean; publicKey: string | null }
export type PushSubscriptionRequest = { endpoint: string; p256dh: string; auth: string; expirationTime: string | null }
export type PushSubscriptionResponse = {
  subscriptionId: number
  enabled: boolean
  expirationTime: string | null
  createdAt: string
  updatedAt: string
}
export const pushApi = {
  async config() { return (await apiClient.get<PushConfig>('/push/config')).data },
  async register(request: PushSubscriptionRequest) {
    return (await apiClient.put<PushSubscriptionResponse>('/push/subscriptions', request)).data
  },
  async get(id: number) { return (await apiClient.get<PushSubscriptionResponse>(`/push/subscriptions/${id}`)).data },
  async disable(id: number) { await apiClient.delete(`/push/subscriptions/${id}`) },
}
