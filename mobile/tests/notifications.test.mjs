import { test } from 'node:test';
import assert from 'node:assert/strict';
import { create, AxiosError } from 'axios';
import { badgeLabel, createNotificationApi, foregroundHandler, localTime, notificationLabel } from '../src/notifications/api.ts';
import { createNotificationStore } from '../src/notifications/store.ts';
import { createAuthSession } from '../src/auth/session.ts';
const original = { notificationId: 1, type: 'ENROLLMENT_ASSIGNED', title: '교육 배정', message: '새 교육입니다.', relatedEnrollmentId: 7, readAt: null, createdAt: '2026-10-03T00:00:00' };
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
function fixture() {
  let rows = [{ ...original }]; const calls = [];
  const api = {
    list: async page => { calls.push(['list', page]); return { content: rows.map(row => ({ ...row })), page, size: 20, totalElements: rows.length, totalPages: rows.length ? 3 : 0 }; },
    count: async () => { calls.push(['count']); return rows.filter(row => !row.readAt).length; },
    get: async id => { calls.push(['get', id]); return { ...rows.find(row => row.notificationId === id) }; },
    read: async id => { calls.push(['read', id]); rows = rows.map(row => row.notificationId === id ? { ...row, readAt: '2026-10-03T01:00:00' } : row); return { ...rows.find(row => row.notificationId === id) }; },
    readAll: async () => { calls.push(['all']); rows = rows.map(row => ({ ...row, readAt: '2026-10-03T01:00:00' })); return { readAt: '2026-10-03T01:00:00' }; },
    checkEnrollment: async id => { calls.push(['enrollment', id]); },
  };
  return { api, calls, store: createNotificationStore(api), setRows: value => { rows = value; } };
}
test('list and count preserve server order and page contract', async () => {
  const client = create(); const requests = [];
  client.defaults.adapter = async c => { requests.push(c); return { config: c, status: 200, statusText: 'OK', headers: {}, data: c.url.endsWith('unread-count') ? { count: 42 } : { content: [{ ...original, notificationId: 2 }, original], page: 1, size: 20, totalElements: 22, totalPages: 2 } }; };
  const api = createNotificationApi(client); const page = await api.list(1); assert.deepEqual(page.content.map(row => row.notificationId), [2, 1]); assert.deepEqual(requests[0].params, { page: 1, size: 20 }); assert.equal(await api.count(), 42); assert.equal(requests[1].url, '/notifications/me/unread-count');
});
test('empty notifications and zero badge', async () => {
  const { store, setRows } = fixture(); setRows([]); await store.focus(true); assert.deepEqual(store.snapshot().data.content, []); assert.equal(badgeLabel(store.snapshot().count), undefined);
});
test('badge uses dedicated count and supports overflow/unknown', () => {
  assert.equal(badgeLabel(3), '3'); assert.equal(badgeLabel(100), '99+'); for(const value of [0, null, -1, NaN]) assert.equal(badgeLabel(value), undefined);
});
test('single read refreshes count then checks enrollment destination', async () => {
  const { store, calls } = fixture(); await store.focus(true); assert.equal(store.snapshot().count, 1); assert.equal(await store.select(1), 7); assert.equal(store.snapshot().count, 0); assert.ok(store.snapshot().data.content[0].readAt); assert.ok(calls.findIndex(c => c[0] === 'read') < calls.findIndex(c => c[0] === 'enrollment'));
});
test('already read notification does not PATCH again', async () => {
  const { store, calls, setRows } = fixture(); setRows([{ ...original, readAt: '2026-10-03T01:00:00' }]); assert.equal(await store.select(1), 7); assert.equal(calls.filter(c => c[0] === 'read').length, 0);
});
test('read all refreshes server count and rows', async () => {
  const { store, calls } = fixture(); await store.focus(true); await store.readAll(); assert.equal(store.snapshot().count, 0); assert.ok(store.snapshot().data.content[0].readAt); assert.equal(calls.filter(c => c[0] === 'all').length, 1);
});
test('no related enrollment remains on notifications', async () => {
  const { store, calls, setRows } = fixture(); setRows([{ ...original, relatedEnrollmentId: null }]); assert.equal(await store.select(1), null); assert.match(store.snapshot().actionError, /연결된 교육/); assert.equal(calls.filter(c => c[0] === 'enrollment').length, 0);
});
test('denied enrollment stays on notifications but preserves successful read', async () => {
  const { store, api } = fixture(); api.checkEnrollment = async () => { throw new Error('403'); }; await store.focus(true); assert.equal(await store.select(1), null); assert.equal(store.snapshot().count, 0); assert.match(store.snapshot().actionError, /접근할 수 없습니다/);
});
test('unknown type and invalid timestamp have safe fallback', () => {
  assert.equal(notificationLabel('NEW_TYPE'), '알림'); assert.equal(notificationLabel('constructor'), '알림'); assert.equal(notificationLabel('COURSE_COMPLETED'), '교육 수료'); assert.equal(localTime('invalid'), '—'); assert.equal(localTime(''), '—'); assert.notEqual(localTime('2026-10-03T00:00:00'), '—'); assert.equal(localTime('2026-10-03T00:00:00'), localTime('2026-10-03T00:00:00Z'));
});
test('pagination fetches requested page and pull refresh/reentry refetch', async () => {
  const { store, calls } = fixture(); await store.focus(true); await store.page(1); assert.equal(store.snapshot().data.page, 1); await store.refresh(); await store.focus(false); await store.focus(true); assert.equal(calls.filter(c => c[0] === 'list').length, 4);
});
test('foreground only refreshes on transition into active', () => {
  let calls = 0; const handler = foregroundHandler(() => calls++, 'active'); handler('active'); handler('background'); handler('inactive'); handler('active'); handler('active'); assert.equal(calls, 1);
});
test('background screen refresh fetches badge only; visible screen includes list', async () => {
  const { store, calls } = fixture(); await store.start(); assert.equal(calls.filter(c => c[0] === 'list').length, 0); await store.refresh(); await store.focus(true); assert.equal(calls.filter(c => c[0] === 'list').length, 1); await store.focus(false); await store.refresh(); assert.equal(calls.filter(c => c[0] === 'list').length, 1);
});
test('duplicate individual and all-read actions are suppressed', async () => {
  const { store, api, calls } = fixture(); const gate = deferred(), originalRead = api.read;
  api.read = async id => { await gate.promise; return originalRead(id); };
  const first = store.select(1); await Promise.resolve(); assert.equal(await store.select(1), null); assert.equal(await store.readAll(), null); gate.resolve(); await first; assert.equal(calls.filter(c => c[0] === 'read').length, 1); assert.equal(calls.filter(c => c[0] === 'all').length, 0);
});
test('duplicate read-all requests do not send second PATCH', async () => {
  const { store, api, calls } = fixture(); const gate = deferred(), readAll = api.readAll; api.readAll = async () => { await gate.promise; return readAll(); };
  const first = store.readAll(); await store.readAll(); gate.resolve(); await first; assert.equal(calls.filter(c => c[0] === 'all').length, 1);
});
test('refresh loading/error/retry without treating failed count as zero', async () => {
  const { store, api } = fixture(); const count = api.count, list = api.list; const gate = deferred(); api.count = async () => { await gate.promise; throw new Error('offline'); }; api.list = async () => { throw new Error('offline'); };
  const refresh = store.focus(true); assert.equal(store.snapshot().loading, true); gate.resolve(); await refresh; assert.equal(store.snapshot().loading, false); assert.equal(store.snapshot().count, null); assert.ok(store.snapshot().error); assert.ok(store.snapshot().countError);
  api.count = count; api.list = list; await store.refresh(); assert.equal(store.snapshot().error, ''); assert.equal(store.snapshot().count, 1);
});
test('failed PATCH reconciles with server instead of optimistic decrement', async () => {
  const { store, api } = fixture(); api.read = async () => { throw new Error('offline'); }; await store.focus(true); assert.equal(await store.select(1), null); assert.equal(store.snapshot().count, 1); assert.equal(store.snapshot().data.content[0].readAt, null); assert.ok(store.snapshot().actionError);
});
test('old count response cannot overwrite read-all result', async () => {
  const { store, api } = fixture(); const gate = deferred(); let first = true; const count = api.count; api.count = async () => { if(first) { first = false; await gate.promise; return 50; } return count(); };
  const old = store.focus(true); await store.readAll(); gate.resolve(); await old; assert.equal(store.snapshot().count, 0);
});
test('logout cancels pending action/navigation and resets badge', async () => {
  const { store, api } = fixture(); const gate = deferred(); api.checkEnrollment = async () => gate.promise; const pending = store.select(1); await new Promise(r => setImmediate(r)); store.stop(); gate.resolve(); assert.equal(await pending, null); assert.equal(store.snapshot().count, null); assert.equal(store.snapshot().data, null);
});
test('provider effect restart can refresh after cleanup', async () => {
  const { store } = fixture(); await store.start(); store.stop(); await store.start(); assert.equal(store.snapshot().count, 1);
});
test('focus before provider effect restart still reloads the visible list', async () => {
  const { store } = fixture(); await store.start(); store.stop(); await store.focus(true); await store.start(); assert.equal(store.snapshot().data.content.length, 1);
});
test('notification detail/read-all/access-check HTTP paths match backend', async () => {
  const client = create(), calls = []; client.defaults.adapter = async c => { calls.push([c.method, c.url]); return { config: c, data: original, status: 200, statusText: 'OK', headers: {} }; };
  const api = createNotificationApi(client); await api.get(1); await api.readAll(); await api.checkEnrollment(7);
  assert.deepEqual(calls, [['get', '/notifications/1'], ['patch', '/notifications/me/read-all'], ['get', '/enrollments/7/progress']]);
});
test('read API uses authenticated refresh and exact PATCH URI', async () => {
  const auth = createAuthSession({ read: async () => null, write: async () => {}, remove: async () => {} }, 'https://example.com/api');
  const token = { accessToken: `a.${Buffer.from(JSON.stringify({ memberId: 1, email: 'a@example.com', roles: ['EMPLOYEE'], tokenType: 'ACCESS', exp: 9999999999 })).toString('base64url')}.b`, refreshToken: 'refresh', tokenType: 'Bearer', accessTokenExpiresInSeconds: 100, refreshTokenExpiresInSeconds: 100 };
  let refreshes = 0; const response = (config, data) => ({ config, data, status: 200, statusText: 'OK', headers: {} }); auth.publicClient.defaults.adapter = async c => { if(c.url.endsWith('/refresh')) refreshes++; return response(c, token); }; await auth.login('a@example.com', 'password');
  auth.client.defaults.adapter = async c => { assert.ok(c.headers.get('Authorization')); assert.equal(c.method, 'patch'); assert.equal(c.url, '/notifications/1/read'); if(!c.authRetried) throw new AxiosError('expired', '', c, null, { ...response(c, {}), status: 401 }); return response(c, { ...original, readAt: '2026-10-03T01:00:00' }); };
  assert.ok((await createNotificationApi(auth.client).read(1)).readAt); assert.equal(refreshes, 1);
});
