import type { AxiosInstance } from 'axios';
import type { Installation, Subscription } from './identity';

export function createPushApi(client: AxiosInstance, cleanupAccess?: string) {
  const config = (identity: Installation, signal?: AbortSignal) => ({ signal,
    headers: { 'X-Installation-Secret': identity.installationSecret, ...(cleanupAccess ? { Authorization: `Bearer ${cleanupAccess}` } : {}) },
    ...(cleanupAccess ? { timeout: 3000 } : {}),
  });
  const base = '/mobile/push/subscriptions';
  return {
    async bind(identity: Installation, platform: Subscription['platform'], version: number, signal?: AbortSignal) {
      return (await client.put<Subscription>(`${base}/binding`, { installationId: identity.installationId, platform, version }, config(identity, signal))).data;
    },
    async get(identity: Installation, id: number, signal?: AbortSignal) {
      return (await client.get<Subscription>(`${base}/${id}`, config(identity, signal))).data;
    },
    async register(identity: Installation, binding: Subscription, token: string, platform: Subscription['platform'], signal?: AbortSignal) {
      return (await client.put<Subscription>(`${base}/${binding.subscriptionId}`, { expoPushToken: token, platform, version: binding.version }, config(identity, signal))).data;
    },
    async disable(identity: Installation, binding: Subscription, signal?: AbortSignal) {
      await client.delete(`${base}/${binding.subscriptionId}`, { ...config(identity, signal), params: { version: binding.version } });
      return { ...binding, enabled: false, version: binding.enabled ? binding.version + 1 : binding.version };
    },
  };
}
