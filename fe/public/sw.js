/* Push only: no fetch handler, cache, tokens, or account storage. */
self.addEventListener('install', event => event.waitUntil(self.skipWaiting()))
self.addEventListener('activate', event => event.waitUntil(self.clients.claim()))

function validId(value) { return Number.isSafeInteger(value) && value > 0 }
async function notifyWindows(type) {
  const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
  for (const client of windows) {
    if (new URL(client.url).origin === self.location.origin) client.postMessage({ type })
  }
}
self.addEventListener('push', event => {
  let payload = null
  try { payload = event.data?.json() } catch { /* Malformed payload still receives a generic notification. */ }
  const id = validId(payload?.notificationId) ? payload.notificationId : null
  const title = typeof payload?.title === 'string' && payload.title.trim() ? payload.title.slice(0, 80) : 'LearnFlow 새 알림'
  const body = typeof payload?.message === 'string' && payload.message.trim()
    ? payload.message.slice(0, 200) : '새 교육 알림이 있습니다. LearnFlow에서 확인하세요.'
  event.waitUntil((async () => {
    try {
      await self.registration.showNotification(title, { body, icon: '/push-icon.svg',
        tag: id ? `learnflow-notification-${id}` : 'learnflow-notification', data: { notificationId: id } })
    } catch { await notifyWindows('LEARNFLOW_PUSH_DISPLAY_FAILED') }
    await notifyWindows('LEARNFLOW_NOTIFICATIONS_CHANGED')
  })())
})
self.addEventListener('notificationclick', event => {
  event.notification.close()
  const id = event.notification.data?.notificationId
  if (!validId(id)) return
  const url = new URL(`/notifications/${id}`, self.location.origin).href
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    for (const client of windows) {
      if (new URL(client.url).origin !== self.location.origin) continue
      try {
        const navigated = await client.navigate(url)
        if (navigated) { await navigated.focus(); return }
      } catch { /* Try another window, then open a new one. */ }
    }
    await self.clients.openWindow(url)
  })())
})
