export function isNotificationSignal(value: unknown): boolean {
  return !!value && typeof value === 'object' && 'type' in value && value.type === 'LEARNFLOW_NOTIFICATIONS_CHANGED'
}
export function listenForNotificationChanges(refresh: () => void, displayError: () => void = () => {}) {
  const worker = typeof navigator !== 'undefined' && 'serviceWorker' in navigator ? navigator.serviceWorker : null
  const receive = (event: MessageEvent<unknown>) => {
    if (isNotificationSignal(event.data)) refresh()
    else if (event.data && typeof event.data === 'object' && 'type' in event.data && event.data.type === 'LEARNFLOW_PUSH_DISPLAY_FAILED') displayError()
  }
  worker?.addEventListener('message', receive)
  if (typeof window !== 'undefined') window.addEventListener('learnflow-notifications-changed', refresh)
  return () => {
    worker?.removeEventListener('message', receive)
    if (typeof window !== 'undefined') window.removeEventListener('learnflow-notifications-changed', refresh)
  }
}
export function signalNotificationChanges() {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event('learnflow-notifications-changed'))
}
