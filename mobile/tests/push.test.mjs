import { test } from 'node:test';
import assert from 'node:assert/strict';
import { create, AxiosError } from 'axios';
import { createInstallationStore } from '../src/push/identity.ts';
import { createPushApi } from '../src/push/api.ts';
import { createPushStore } from '../src/push/store.ts';
import { createPushClicks } from '../src/push/clicks.ts';
import { notificationId, resolveNotification } from '../src/notifications/resolve.ts';
import { createAuthSession } from '../src/auth/session.ts';
import { createNotificationStore } from '../src/notifications/store.ts';

const tick = () => new Promise(r => setImmediate(r));
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
const failure = status => new AxiosError('failed', undefined, undefined, undefined, { status });
const id = '11111111-1111-4111-a111-111111111111', secret = 's'.repeat(64);
function fixture() {
  let raw = null, owner = null, current = null, token = '', permission = 'undetermined';
  const calls = [];
  const identity = createInstallationStore({ read: async () => raw, write: async value => { raw = value; } }, () => ({ id, secret }));
  const api = {
    get: async () => { calls.push('get'); if (!current) throw failure(404); return { ...current }; },
    bind: async (_, platform, version) => {
      calls.push('bind');
      if (current && current.version !== version) throw failure(409);
      if (!current) current = { subscriptionId: 1, version: 0, platform, enabled: false, updatedAt: '2026-10-05T00:00:00Z' };
      else if (owner !== actor) current = { ...current, enabled: false, version: current.version + 1 };
      owner = actor; return { ...current };
    },
    register: async (_, binding, nextToken) => { calls.push('register'); assert.equal(binding.version, current.version); token = nextToken; current = { ...current, enabled: true, version: current.version + 1 }; return { ...current }; },
    disable: async (_, binding) => { calls.push('disable'); assert.equal(binding.version, current.version); current = { ...current, enabled: false, version: current.version + Number(current.enabled) }; return { ...current }; },
  };
  let actor = 1;
  const native = { platform: 'ANDROID', blocker: '', channel: async () => { calls.push('channel'); }, permission: async () => { calls.push('permission'); return permission; }, requestPermission: async () => { calls.push('request'); permission = 'granted'; return permission; }, token: async () => { calls.push('token'); return 'ExpoPushToken[first]'; } };
  const store = createPushStore(api, identity, native);
  return { api, identity, native, store, calls, raw: () => raw, token: () => token, current: () => current, actor: value => { actor = value; }, permission: value => { permission = value; } };
}

test('installation generates once, persists separate credentials and reuses them', async () => {
  const f = fixture(); const [a, b] = await Promise.all([f.identity.get(), f.identity.get()]);
  assert.equal(a.installationId, id); assert.equal(b.installationSecret, secret);
  const restored = createInstallationStore({ read: async () => f.raw(), write: async () => {} }, () => { throw Error('must reuse'); });
  assert.deepEqual(await restored.get(), a);
});
test('corrupt identity is not silently replaced with a different installation', async () => {
  const identity = createInstallationStore({ read: async () => '{}', write: async () => {} }, () => ({ id, secret }));
  await assert.rejects(identity.get());
});
test('login binds without prompting permission; explicit ON preserves channel order', async () => {
  const f = fixture(); await f.store.setMember(1); assert.equal(f.store.snapshot().enabled, false); assert.ok(!f.calls.includes('request'));
  f.calls.length = 0; await f.store.on(); assert.deepEqual(f.calls, ['channel', 'permission', 'request', 'token', 'get', 'register']); assert.equal(f.store.snapshot().enabled, true);
});
test('permission denial remains OFF and is not requested repeatedly', async () => {
  const f = fixture(); f.native.requestPermission = async () => { f.calls.push('request'); f.permission('denied'); return 'denied'; };
  await f.store.setMember(1); await f.store.on(); await f.store.on();
  assert.equal(f.calls.filter(c => c === 'request').length, 1); assert.equal(f.store.snapshot().permission, 'denied'); assert.equal(f.store.snapshot().enabled, false); assert.ok(!f.calls.includes('register'));
});
test('already granted permission does not request and server failure is not ON', async () => {
  const f = fixture(); f.permission('granted'); f.api.register = async () => { throw failure(500); };
  await f.store.setMember(1); await f.store.on(); assert.equal(f.store.snapshot().phase, 'registration-error'); assert.equal(f.store.snapshot().enabled, false); assert.ok(!f.calls.includes('request'));
});
test('token issuance failure is distinct and retryable', async () => {
  const f = fixture(); await f.store.setMember(1); f.native.token = async () => { throw Error('offline'); }; await f.store.on(); assert.equal(f.store.snapshot().phase, 'token-error');
  f.native.token = async () => 'ExpoPushToken[recovered]'; await f.store.on(); assert.equal(f.store.snapshot().enabled, true);
});
test('binding failure prevents activation and retry recovers', async () => {
  const f = fixture(), bind = f.api.bind; f.api.bind = async () => { throw failure(500); };
  await f.store.setMember(1); assert.equal(f.store.snapshot().phase, 'binding-error');
  f.api.bind = bind; await f.store.retry(); assert.equal(f.store.snapshot().phase, 'off');
});
test('missing project/development build prevents permission prompt and registration', async () => {
  const f = fixture(); f.native.blocker = '설정 필요'; await f.store.setMember(1); await f.store.on(); assert.equal(f.store.snapshot().enabled, false); assert.ok(!f.calls.includes('request')); assert.ok(!f.calls.includes('token'));
});
test('repeated ON is single-flight while a token is pending', async () => {
  const f = fixture(), gate = deferred(); await f.store.setMember(1); f.native.token = async () => { await gate.promise; return 'ExpoPushToken[first]'; };
  const first = f.store.on(); const second = f.store.on(); gate.resolve(); await Promise.all([first, second]); assert.equal(f.calls.filter(c => c === 'register').length, 1);
});
test('foreground/token change obtains Expo token again without asking permission', async () => {
  const f = fixture(); await f.store.setMember(1); await f.store.on(); f.calls.length = 0;
  f.native.token = async () => 'ExpoPushToken[changed]'; await f.store.reconcile(); assert.equal(f.token(), 'ExpoPushToken[changed]'); assert.ok(!f.calls.includes('request'));
});
test('unchanged token avoids redundant registration and OFF foreground never activates', async () => {
  const f = fixture(); await f.store.setMember(1); await f.store.on(); f.calls.length = 0;
  await f.store.reconcile(); assert.ok(!f.calls.includes('register')); await f.store.off(); f.calls.length = 0;
  await f.store.reconcile(); assert.deepEqual(f.calls, ['permission']); assert.equal(f.store.snapshot().enabled, false);
});
test('OS permission revoked disables server subscription', async () => {
  const f = fixture(); await f.store.setMember(1); await f.store.on(); f.permission('denied'); await f.store.reconcile(); assert.equal(f.current().enabled, false); assert.equal(f.store.snapshot().permission, 'denied');
});
test('OFF saves disabled version and leaves permission untouched', async () => {
  const f = fixture(); await f.store.setMember(1); await f.store.on(); await f.store.off(); assert.equal(f.current().enabled, false); assert.equal(f.store.snapshot().permission, 'granted'); assert.equal((await f.identity.get()).binding.version, f.current().version);
});
test('OFF failure does not pretend remote subscription was disabled', async () => {
  const f = fixture(); await f.store.setMember(1); await f.store.on(); f.api.disable = async () => { throw failure(500); }; await f.store.off(); assert.equal(f.store.snapshot().phase, 'off-error'); assert.equal(f.store.snapshot().enabled, true);
});
test('A logout and B binding use the same installation and start OFF', async () => {
  const f = fixture(); await f.store.setMember(1); await f.store.on(); await f.store.logout(f.api); f.actor(2); await f.store.setMember(2);
  const saved = await f.identity.get(); assert.equal(saved.installationId, id); assert.equal(saved.installationSecret, secret); assert.equal(saved.memberId, 2); assert.equal(f.store.snapshot().enabled, false);
});
test('same account restart restores opt-in without permission prompt', async () => {
  const f = fixture(); await f.store.setMember(1); await f.store.on(); f.calls.length = 0;
  const restored = createPushStore(f.api, f.identity, f.native); await restored.setMember(1); assert.equal(restored.snapshot().enabled, true); assert.ok(!f.calls.includes('request'));
});
test('logout best-effort failure preserves identity and leaves local OFF', async () => {
  const f = fixture(); await f.store.setMember(1); await f.store.on(); const api = { ...f.api, disable: async () => { throw Error('offline'); } };
  await f.store.logout(api); assert.equal(f.store.snapshot().enabled, false); assert.equal((await f.identity.get()).optedInMember, null); assert.equal((await f.identity.get()).installationSecret, secret);
});
test('late registration after logout cannot publish ON and cleanup uses fresh version', async () => {
  const f = fixture(), gate = deferred(), register = f.api.register; await f.store.setMember(1);
  f.api.register = async (...args) => { await gate.promise; return register(...args); };
  const enabling = f.store.on(); await tick(); const logout = f.store.logout(f.api); assert.equal(f.store.snapshot().enabled, false); gate.resolve(); await Promise.all([enabling, logout]); assert.equal(f.store.snapshot().enabled, false); assert.equal(f.current().enabled, false);
});
test('stale binding response never overwrites next account state', async () => {
  const f = fixture(), gate = deferred(), bind = f.api.bind;
  f.api.bind = async (...args) => { await gate.promise; return bind(...args); }; const a = f.store.setMember(1); await tick();
  f.api.bind = bind; f.actor(2); await f.store.setMember(2); gate.resolve(); await a; assert.equal((await f.identity.get()).memberId, 2); assert.equal(f.store.snapshot().enabled, false);
});
test('unknown cross-account version conflict remains a visible failure, never guessed', async () => {
  const f = fixture(); await f.store.setMember(1); await f.store.on(); f.api.get = async () => { throw failure(404); }; f.api.bind = async () => { throw failure(409); }; await f.store.setMember(2); assert.equal(f.store.snapshot().phase, 'binding-error'); assert.equal(f.store.snapshot().enabled, false);
});

test('API uses exact header, binding/version, PUT and DELETE contracts', async () => {
  const client = create(), requests = []; client.defaults.adapter = async config => { requests.push(config); return { config, data: { subscriptionId: 5, version: 7, enabled: true }, status: 200, statusText: 'OK', headers: {} }; };
  const api = createPushApi(client), identity = { installationId: id, installationSecret: secret }, binding = { subscriptionId: 5, version: 7, enabled: true };
  await api.bind(identity, 'IOS', 6); await api.register(identity, binding, 'ExpoPushToken[test]', 'IOS'); await api.disable(identity, binding);
  assert.equal(requests[0].url, '/mobile/push/subscriptions/binding'); assert.equal(requests[0].headers.get('X-Installation-Secret'), secret); assert.deepEqual(JSON.parse(requests[0].data), { installationId: id, platform: 'IOS', version: 6 });
  assert.equal(requests[1].method, 'put'); assert.equal(JSON.parse(requests[1].data).version, 7); assert.equal(requests[2].method, 'delete'); assert.deepEqual(requests[2].params, { version: 7 }); assert.ok(!requests.some(r => r.url.includes(secret)));
});

function clicksFixture() {
  const calls = [], clear = []; const clicks = createPushClicks(async () => { clear.push(true); });
  const attach = () => clicks.attach(async id => { calls.push(['get', id]); return { destination: 7, retryable: false }; }, id => calls.push(['move', id]));
  return { clicks, calls, clear, attach };
}
test('background click processes once despite duplicate initial response', async () => {
  const f = clicksFixture(); f.clicks.session(1); f.attach(); f.clicks.receive({ key: 'a', value: '1' }); f.clicks.receive({ key: 'a', value: 1 }); await tick(); f.clicks.receive({ key: 'a', value: 1 }); await tick(); assert.deepEqual(f.calls, [['get', 1], ['move', 7]]); assert.equal(f.clear.length, 1);
});
test('cold start waits for authentication AND mounted navigation', async () => {
  const f = clicksFixture(); f.clicks.receive({ key: 'a', value: 1 }); f.clicks.session(1); await tick(); assert.deepEqual(f.calls, []); f.attach(); await tick(); assert.deepEqual(f.calls, [['get', 1], ['move', 7]]);
});
test('logged-out click survives login and then opens original notification', async () => {
  const f = clicksFixture(); f.attach(); f.clicks.receive({ key: 'a', value: 2 }); await tick(); assert.deepEqual(f.calls, []); f.clicks.session(1); await tick(); assert.equal(f.calls[0][1], 2);
});
test('account change cancels pending click/navigation', async () => {
  const f = clicksFixture(), gate = deferred(); f.clicks.session(1);
  f.clicks.attach(async () => { await gate.promise; return { destination: 7, retryable: false }; }, id => f.calls.push(id));
  f.clicks.receive({ key: 'a', value: 1 }); f.clicks.session(2); gate.resolve(); await tick(); assert.deepEqual(f.calls, []);
});
test('invalid notification identifiers and route payloads cannot navigate', async () => {
  for (const value of [null, true, '', '1e2', '../1', -1, 0, {}, Infinity, 9007199254740992]) assert.equal(notificationId(value), null);
  const f = clicksFixture(); f.clicks.session(1); f.attach(); f.clicks.receive({ key: 'bad', value: { route: '/admin' } }); await tick(); assert.deepEqual(f.calls, []);
});
test('temporary click failure has explicit retry, then clears initial response', async () => {
  const f = clicksFixture(); let failing = true; f.clicks.session(1);
  f.clicks.attach(async () => ({ destination: failing ? null : 7, retryable: failing }), id => f.calls.push(id));
  f.clicks.receive({ key: 'a', value: 1 }); await tick(); assert.ok(f.clicks.snapshot().error); assert.deepEqual(f.calls, [null]); failing = false; await f.clicks.retry(); assert.deepEqual(f.calls, [null, 7]); assert.equal(f.clear.length, 1);
});
test('ownership failure never marks read or checks enrollment', async () => {
  const calls = [], api = { get: async () => { throw failure(404); }, read: async () => calls.push('read'), checkEnrollment: async () => calls.push('check') };
  await assert.rejects(resolveNotification(api, 1, new AbortController().signal)); assert.deepEqual(calls, []);
});
test('foreign notification click returns to notifications with no retry or private content', async () => {
  const moves = [], api = { get: async () => { throw failure(404); }, count: async () => 0 };
  const store = createNotificationStore(api); const clicks = createPushClicks(async () => {}); clicks.session(1);
  clicks.attach(async id => ({ destination: await store.select(id), retryable: store.snapshot().actionRetryable }), destination => moves.push(destination));
  clicks.receive({ key: 'foreign', value: 4 }); await tick(); assert.deepEqual(moves, [null]); assert.equal(store.snapshot().data, null); assert.equal(clicks.snapshot().error, '');
});
test('shared click resolver reads own notification then checks server-related enrollment', async () => {
  const calls = []; const n = { notificationId: 1, readAt: null, relatedEnrollmentId: 7 };
  const api = { get: async () => { calls.push('get'); return n; }, read: async () => { calls.push('read'); return { ...n, readAt: 'now' }; }, checkEnrollment: async id => { calls.push(id); throw failure(403); } };
  const result = await resolveNotification(api, 1, new AbortController().signal); assert.deepEqual(calls, ['get', 'read', 7]); assert.equal(result.destination, null); assert.equal(result.retryable, false);
});
test('foreground refresh uses DB count and visible list without marking read', async () => {
  const calls = [], api = { count: async () => { calls.push('count'); return 9; }, list: async () => { calls.push('list'); return { content: [], page: 0, size: 20, totalPages: 0, totalElements: 0 }; } };
  const store = createNotificationStore(api); await store.start(); calls.length = 0; await store.refresh(); assert.deepEqual(calls, ['count']); await store.focus(true); calls.length = 0; await store.refresh(); assert.deepEqual(calls, ['count', 'list']); assert.equal(store.snapshot().count, 9);
});

const tokens = revision => ({ accessToken: `e30.${Buffer.from(JSON.stringify({ memberId: 1, email: 'a@b.co', roles: ['EMPLOYEE'], tokenType: 'ACCESS', exp: Math.floor(Date.now() / 1000) + 3600, revision })).toString('base64url')}.sig`, refreshToken: `refresh-${revision}`, tokenType: 'Bearer', accessTokenExpiresInSeconds: 3600, refreshTokenExpiresInSeconds: 86400 });
test('Push API shares 401 single-flight rotation and retries original request once', async () => {
  let saved = null, refreshes = 0;
  const session = createAuthSession({ read: async () => saved, write: async value => { saved = value; }, remove: async () => { saved = null; } }, 'https://example.com/api');
  session.publicClient.defaults.adapter = async config => { if (config.url === '/auth/refresh') refreshes++; return { config, data: tokens(refreshes), status: 200, statusText: 'OK', headers: {} }; };
  await session.login('a@b.co', 'password');
  session.client.defaults.adapter = async config => {
    if (config.headers.get('Authorization') === `Bearer ${tokens(0).accessToken}`) throw new AxiosError('401', undefined, config, undefined, { status: 401 });
    return { config, data: { subscriptionId: 1 }, status: 200, statusText: 'OK', headers: {} };
  };
  const api = createPushApi(session.client); await Promise.all([api.get({ installationSecret: secret }, 1), api.get({ installationSecret: secret }, 1)]); assert.equal(refreshes, 1); assert.equal(JSON.parse(saved).refreshToken, 'refresh-1');
});
test('auth invalidation starts best-effort cleanup without waiting for it', async () => {
  let saved = null; const gate = deferred(), events = [];
  const session = createAuthSession({ read: async () => saved, write: async value => { saved = value; }, remove: async () => { saved = null; } }, 'https://example.com/api');
  session.publicClient.defaults.adapter = async config => ({ config, data: tokens(0), status: 200, statusText: 'OK', headers: {} });
  await session.login('a@b.co', 'password'); session.onInvalidate(access => { events.push(access); void gate.promise; });
  const logout = session.logout(); assert.equal(session.getSnapshot().status, 'anonymous'); await logout; assert.equal(saved, null); assert.equal(events.length, 1); gate.resolve();
});
