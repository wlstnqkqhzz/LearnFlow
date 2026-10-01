import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'

const code = await readFile(new URL('../public/sw.js', import.meta.url), 'utf8')
function worker({ windows = [], failDisplay = false } = {}) {
  const handlers = new Map(); const shown = []; const opened = []; const lifecycle = []
  const self = { location: { origin: 'https://learnflow.test' },
    addEventListener: (type, handler) => handlers.set(type, handler),
    async skipWaiting() { lifecycle.push('skipWaiting') },
    registration: { async showNotification(title, options) { if (failDisplay) throw Error('blocked'); shown.push({ title, ...options }) } },
    clients: { async claim() { lifecycle.push('claim') }, async matchAll(options) { assert.deepEqual({ ...options }, { type: 'window', includeUncontrolled: true }); return windows },
      async openWindow(url) { opened.push(url) } },
  }
  vm.runInNewContext(code, { self, URL })
  async function emit(type, details = {}) { let work; handlers.get(type)({ ...details, waitUntil(promise) { work = promise } }); await work }
  return { handlers, shown, opened, lifecycle, emit }
}
function client(url = 'https://learnflow.test/employee') {
  const events = []
  return { url, events, postMessage(message) { events.push(['message', message.type]) },
    async navigate(target) { events.push(['navigate', target]); return this }, async focus() { events.push(['focus']) } }
}
await test('Service Worker는 루트 Push 전용이며 fetch/cache 처리 없이 활성화한다', async () => {
  const sw = worker(); assert.deepEqual([...sw.handlers.keys()], ['install', 'activate', 'push', 'notificationclick'])
  await sw.emit('install'); await sw.emit('activate'); assert.deepEqual(sw.lifecycle, ['skipWaiting', 'claim'])
})
await test('push는 최소 내용만 표시하고 같은 origin 앱에 재조회 신호를 보낸다', async () => {
  const app = client(); const foreign = client('https://evil.test'); const sw = worker({ windows: [app, foreign] })
  await sw.emit('push', { data: { json: () => ({ notificationId: 42, title: '새 알림', message: 'LearnFlow에서 확인하세요.', token: 'secret', name: 'private', redirect: 'https://evil.test' }) } })
  assert.equal(sw.shown[0].title, '새 알림'); assert.equal(sw.shown[0].body, 'LearnFlow에서 확인하세요.')
  assert.deepEqual(JSON.parse(JSON.stringify(sw.shown[0].data)), { notificationId: 42 })
  assert.equal(sw.shown[0].tag, 'learnflow-notification-42'); assert.doesNotMatch(JSON.stringify(sw.shown), /secret|private|evil/)
  assert.deepEqual(app.events, [['message', 'LEARNFLOW_NOTIFICATIONS_CHANGED']]); assert.deepEqual(foreign.events, [])
})
await test('손상/빈 payload는 generic 알림으로 표시하고 표시만으로 읽음 처리하지 않는다', async () => {
  const sw = worker()
  await sw.emit('push', { data: { json() { throw Error('malformed') } } }); await sw.emit('push')
  assert.equal(sw.shown.length, 2); assert.equal(sw.shown[0].data.notificationId, null)
  assert.match(sw.shown[0].body, /LearnFlow/); assert.deepEqual(sw.opened, [])
})
await test('OS 알림 표시 실패도 열린 앱에 UI 오류와 DB 재조회 신호를 전달한다', async () => {
  const app = client(); const sw = worker({ windows: [app], failDisplay: true }); await sw.emit('push')
  assert.deepEqual(app.events, [['message', 'LEARNFLOW_PUSH_DISPLAY_FAILED'], ['message', 'LEARNFLOW_NOTIFICATIONS_CHANGED']])
})
await test('notificationclick은 기존 동일 origin 창을 고정된 내부 경로로 이동하고 focus한다', async () => {
  const app = client(); const foreign = client('https://evil.test'); const sw = worker({ windows: [foreign, app] }); let closed = false
  await sw.emit('notificationclick', { notification: { data: { notificationId: 42, url: 'https://evil.test' }, close() { closed = true } } })
  assert.equal(closed, true); assert.deepEqual(app.events, [['navigate', 'https://learnflow.test/notifications/42'], ['focus']]); assert.deepEqual(sw.opened, [])
  assert.deepEqual(foreign.events, [])
})
await test('앱 창이 없거나 이동에 실패하면 새 창으로 알림 Route를 연다', async () => {
  const broken = client(); broken.navigate = async () => { throw Error('closed') }
  for (const windows of [[], [broken]]) {
    const sw = worker({ windows }); await sw.emit('notificationclick', { notification: { data: { notificationId: 7 }, close() {} } })
    assert.deepEqual(sw.opened, ['https://learnflow.test/notifications/7'])
  }
})
await test('외부 URL/문자열/잘못된 ID는 click 이동에 사용하지 않는다', async () => {
  for (const notificationId of ['//evil.test', '1', -1, 0, null, 9007199254740992]) {
    const sw = worker(); await sw.emit('notificationclick', { notification: { data: { notificationId }, close() {} } }); assert.deepEqual(sw.opened, [])
  }
})
