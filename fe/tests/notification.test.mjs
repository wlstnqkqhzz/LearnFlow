import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'
import { apiClient } from '../src/api/client.ts'
import { notificationApi } from '../src/api/notificationApi.ts'
import { authSession } from '../src/auth/authSession.ts'
import { createNotificationStore, notificationPath } from '../src/components/notification/notificationStore.ts'

const storage = new Map()
Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, value: {
  getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key),
} })
const items = [
  { notificationId: 2, type: 'COURSE_COMPLETED', title: '교육과정을 수료했습니다.', message: '보안 교육과정을 수료했습니다.', relatedEnrollmentId: 10, readAt: null, createdAt: '2026-09-26T02:00:00' },
  { notificationId: 1, type: 'ENROLLMENT_ASSIGNED', title: '새로운 교육이 배정되었습니다.', message: '보안 교육이 배정되었습니다.', relatedEnrollmentId: 10, readAt: '2026-09-26T01:00:00', createdAt: '2026-09-26T00:00:00' },
]
const readAt = '2026-09-26T03:00:00'
function login(id = 1) {
  authSession.clear()
  authSession.accept({ accessToken: `test.${Buffer.from(JSON.stringify({ memberId: id, email: 'test@example.com', roles: ['EMPLOYEE'], tokenType: 'ACCESS', exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url')}.test`,
    refreshToken: 'test-refresh', tokenType: 'Bearer', accessTokenExpiresInSeconds: 3600, refreshTokenExpiresInSeconds: 7200 }, authSession.getGeneration())
}
function adapter(handler) {
  apiClient.defaults.adapter = async config => ({ config, data: await handler(config), status: 200, statusText: '', headers: {} })
}
function inbox() {
  let rows = structuredClone(items)
  const calls = []
  adapter(config => {
    calls.push([config.method, config.url])
    if (config.url.endsWith('/unread-count')) return { count: rows.filter(item => !item.readAt).length }
    if (config.method === 'get') return { content: rows, page: 0, size: 20, totalElements: 2, totalPages: 1 }
    if (config.url.endsWith('/read-all')) { rows = rows.map(item => ({ ...item, readAt: item.readAt ?? readAt })); return { readAt } }
    rows = rows.map(item => item.notificationId === 2 ? { ...item, readAt } : item)
    return rows[0]
  })
  return calls
}
function deferred() { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }

await test('알림 API는 본인 경로·pagination을 사용하고 읽음 요청에 회원 ID를 보내지 않는다', async () => {
  const calls = []
  adapter(config => { calls.push(config); return {} })
  await notificationApi.list(1, 20); await notificationApi.unread(); await notificationApi.read(2); await notificationApi.readAll()
  assert.deepEqual(calls.map(c => [c.method, c.url]), [['get', '/notifications/me'], ['get', '/notifications/me/unread-count'], ['patch', '/notifications/2/read'], ['patch', '/notifications/me/read-all']])
  assert.deepEqual(calls[0].params, { page: 1, size: 20 })
  assert.equal(calls[2].data, undefined); assert.equal(calls[3].data, undefined)
})
await test('비로그인 상태에서는 Bell 조회를 요청해도 API를 호출하지 않는다', async () => {
  authSession.clear()
  adapter(() => assert.fail('anonymous API call'))
  const store = createNotificationStore()
  await store.load(true); await store.togglePanel()
  assert.equal(store.getSnapshot().open, false)
})
await test('인증 진입은 count만, Bell 열기는 최근 목록과 count를 조회하고 닫기는 요청하지 않는다', async () => {
  login(); const calls = inbox(); const store = createNotificationStore()
  await store.load()
  assert.deepEqual(calls, [['get', '/notifications/me/unread-count']])
  assert.equal(store.getSnapshot().count, 1)
  await store.togglePanel()
  assert.equal(store.getSnapshot().open, true)
  assert.deepEqual(store.getSnapshot().items, items)
  const count = calls.length
  await store.togglePanel()
  assert.equal(store.getSnapshot().open, false)
  assert.equal(calls.length, count)
})
await test('개별 읽음 성공 시 서버 시각과 count를 반영하고 이미 읽은 항목은 PATCH 생략', async () => {
  login(); const calls = inbox(); const store = createNotificationStore()
  await store.load(true)
  assert.equal(await store.read(items[0]), true)
  assert.equal(store.getSnapshot().items[0].readAt, readAt)
  assert.equal(store.getSnapshot().count, 0)
  await store.read(store.getSnapshot().items[0])
  assert.equal(calls.filter(([method]) => method === 'patch').length, 1)
})
await test('모두 읽음은 count 0과 전체 읽음 상태를 반영하고 기존 readAt을 보존', async () => {
  login(); inbox(); const store = createNotificationStore()
  await store.load(true); await store.readAll()
  assert.equal(store.getSnapshot().count, 0)
  assert.ok(store.getSnapshot().items.every(item => item.readAt))
  assert.equal(store.getSnapshot().items[1].readAt, items[1].readAt)
})
await test('읽음 실패는 상태를 변경하지 않고 안전한 메시지와 실패 결과를 반환', async () => {
  login(); inbox(); const store = createNotificationStore(); await store.load(true)
  adapter(() => { throw new Error('SQL private token') })
  assert.equal(await store.read(items[0]), false)
  assert.equal(store.getSnapshot().count, 1)
  assert.equal(store.getSnapshot().items[0].readAt, null)
  assert.match(store.getSnapshot().error, /다시 시도/)
  assert.doesNotMatch(store.getSnapshot().error, /SQL|private|token/)
})
await test('모두 읽음 이후 재조회 실패에도 표시 알림은 읽음 상태로 유지', async () => {
  login(); inbox(); const store = createNotificationStore(); await store.load(true)
  adapter(config => { if (config.method === 'patch') return { readAt }; throw new Error('offline') })
  await store.readAll()
  assert.equal(store.getSnapshot().count, 0)
  assert.ok(store.getSnapshot().items.every(item => item.readAt))
  assert.match(store.getSnapshot().error, /불러오지 못했습니다/)
})
await test('계정 전환 시 이전 목록과 늦은 응답을 노출하지 않는다', async () => {
  login(); inbox(); const old = createNotificationStore(); await old.load(true)
  const waiting = deferred()
  adapter(async () => { await waiting.promise; return { count: 99 } })
  const pending = old.load()
  login(2)
  assert.equal(old.getSnapshot().count, 0)
  assert.deepEqual(old.getSnapshot().items, [])
  waiting.resolve(); await pending
  const next = createNotificationStore()
  assert.deepEqual(next.getSnapshot().items, [])
  assert.equal(old.getSnapshot().count, 0)
  authSession.clear()
  assert.equal(next.getSnapshot().count, 0)
})
await test('알림 이동은 EMPLOYEE의 관련 수강만 사용하며 null ID와 ADMIN-only를 안전하게 처리', () => {
  assert.equal(notificationPath(items[0], true), '/employee/learning/10')
  assert.equal(notificationPath({ ...items[0], relatedEnrollmentId: null }, true), null)
  assert.equal(notificationPath(items[0], false), null)
})
await test('Header 이탈 중 도착한 읽음 응답은 화면 이동을 허용하지 않는다', async () => {
  login(); inbox(); const store = createNotificationStore(); await store.load(true)
  const waiting = deferred()
  adapter(async () => { await waiting.promise; return { ...items[0], readAt } })
  const pending = store.read(items[0])
  store.cancel()
  waiting.resolve()
  assert.equal(await pending, false)
})
await test('목록 조회 오류는 패널 오류 상태만 만들고 재조회 성공으로 복구된다', async () => {
  login(); const store = createNotificationStore()
  adapter(() => { throw new Error('private') })
  await store.togglePanel()
  assert.equal(store.getSnapshot().open, true)
  assert.match(store.getSnapshot().error, /불러오지 못했습니다/)
  inbox(); await store.load(true)
  assert.equal(store.getSnapshot().error, '')
  assert.equal(store.getSnapshot().items.length, 2)
})

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
try {
  const { NotificationBadge, NotificationList } = await server.ssrLoadModule('/src/components/notification/NotificationBell.tsx')
  await test('Badge는 0에서 숨기고 양수와 99+를 compact 표시', () => {
    assert.equal(renderToStaticMarkup(h(NotificationBadge, { count: 0 })), '')
    assert.match(renderToStaticMarkup(h(NotificationBadge, { count: 3 })), />3</)
    assert.match(renderToStaticMarkup(h(NotificationBadge, { count: 100 })), />99\+</)
  })
  await test('목록은 최신순을 유지하며 제목·내용·서울 시각·읽음 상태를 표시', () => {
    const html = renderToStaticMarkup(h(NotificationList, { items, busy: false, loading: false, onSelect() {} }))
    assert.ok(html.indexOf(items[0].title) < html.indexOf(items[1].title))
    assert.ok(html.includes(items[0].message))
    assert.match(html, /읽지 않음/); assert.match(html, /읽음/)
    assert.match(html, /<button/); assert.match(html, /<time/); assert.match(html, /11:00/)
  })
  await test('Empty State와 처리 중 비활성 상태를 렌더링', () => {
    assert.match(renderToStaticMarkup(h(NotificationList, { items: [], busy: false, loading: false, onSelect() {} })), /새로운 알림이 없습니다/)
    assert.match(renderToStaticMarkup(h(NotificationList, { items, busy: true, loading: false, onSelect() {} })), /disabled=""/)
  })
} finally { await server.close(); authSession.clear() }
