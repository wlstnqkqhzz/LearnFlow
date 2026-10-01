import type { PushSubscriptionRequest } from '../api/pushApi.ts'

export type BrowserSubscription = Pick<PushSubscription, 'endpoint' | 'expirationTime' | 'toJSON' | 'unsubscribe'>
export interface PushBrowser {
  unsupportedReason(): string
  permission(): NotificationPermission
  requestPermission(): Promise<NotificationPermission>
  prepare(): Promise<void>
  current(): Promise<BrowserSubscription | null>
  subscribe(key: string): Promise<BrowserSubscription>
  fingerprint(subscription: BrowserSubscription): Promise<string>
  read(): string | null
  write(value: string): void
  lock<T>(action: () => Promise<T>): Promise<T>
}
export const PUSH_STORAGE_KEY = 'learnflow.push.binding.v1'

export function vapidBytes(value: string): Uint8Array<ArrayBuffer> {
  const decoded = atob(value.replace(/-/g, '+').replace(/_/g, '/'))
  const bytes = Uint8Array.from(decoded, char => char.charCodeAt(0))
  if (bytes.length !== 65 || bytes[0] !== 4) throw new Error('Push 공개키 설정을 확인할 수 없습니다.')
  return bytes
}
export function subscriptionRequest(subscription: BrowserSubscription): PushSubscriptionRequest {
  const keys = subscription.toJSON().keys
  if (!keys?.p256dh || !keys.auth) throw new Error('브라우저 구독 정보를 읽을 수 없습니다.')
  return { endpoint: subscription.endpoint, p256dh: keys.p256dh, auth: keys.auth,
    expirationTime: subscription.expirationTime == null ? null : new Date(subscription.expirationTime).toISOString() }
}
function bounded<T>(promise: Promise<T>, milliseconds = 10000): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('브라우저 응답 시간이 초과되었습니다. 다시 시도해 주세요.')), milliseconds)
    promise.then(resolve, reject).finally(() => clearTimeout(timer))
  })
}
export function createPushBrowser(): PushBrowser {
  let registration: ServiceWorkerRegistration | null = null
  return {
    unsupportedReason() {
      if (typeof window === 'undefined' || !window.isSecureContext) return 'Push 알림은 HTTPS 또는 localhost에서 사용할 수 있습니다.'
      if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window))
        return '이 환경은 브라우저 Push를 지원하지 않습니다. iPhone·iPad는 홈 화면에 추가한 웹 앱에서 다시 확인해 주세요.'
      if (!navigator.locks) return '이 브라우저는 안전한 구독 관리 기능을 지원하지 않습니다. 최신 브라우저를 이용해 주세요.'
      return ''
    },
    permission: () => typeof Notification === 'undefined' ? 'default' : Notification.permission,
    requestPermission: () => Notification.requestPermission(),
    async prepare() {
      await bounded(navigator.serviceWorker.register('/sw.js', { scope: '/', updateViaCache: 'none' }))
      registration = await bounded(navigator.serviceWorker.ready)
      if (registration.scope !== `${location.origin}/`) throw new Error('Push 알림 등록 범위를 확인할 수 없습니다.')
    },
    async current() {
      const registered = registration ?? await bounded(navigator.serviceWorker.getRegistration('/'))
      return registered ? bounded(registered.pushManager.getSubscription()) : null
    },
    async subscribe(key) {
      if (!registration) throw new Error('알림 설정을 먼저 준비해 주세요.')
      return registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: vapidBytes(key) })
    },
    async fingerprint(subscription) {
      const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(subscription.endpoint))
      return Array.from(new Uint8Array(bytes), byte => byte.toString(16).padStart(2, '0')).join('')
    },
    read: () => localStorage.getItem(PUSH_STORAGE_KEY),
    write: value => localStorage.setItem(PUSH_STORAGE_KEY, value),
    lock: action => navigator.locks.request('learnflow-push-subscription', action),
  }
}
