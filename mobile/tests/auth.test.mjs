import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AxiosError } from 'axios';
import { createAuthSession, apiAddress, loginValidation } from '../src/auth/session.ts';

const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
const token = (roles = ['EMPLOYEE'], sequence = 1) => ({ accessToken: `header.${Buffer.from(JSON.stringify({ tokenType: 'ACCESS', memberId: 1, email: 'employee@example.com', roles, exp: Math.floor(Date.now()/1000)+3600, sequence })).toString('base64url')}.signature`, refreshToken: `refresh-${sequence}`, tokenType: 'Bearer', accessTokenExpiresInSeconds: 3600, refreshTokenExpiresInSeconds: 86400 });
function setup(initial = null) {
  let value = initial;
  const storage = { read: async () => value, write: async v => { value = v; }, remove: async () => { value = null; } };
  const session = createAuthSession(storage, 'http://192.168.0.9:8080/api');
  return { session, storage, saved: () => value };
}
const ok = (config, data = {}) => ({ config, data, status: 200, statusText: 'OK', headers: {} });
const fail = (config, status) => { throw new AxiosError('Request failed', 'ERR_BAD_RESPONSE', config, null, { ...ok(config), status }); };
async function login(session, roles) { session.publicClient.defaults.adapter = async c => ok(c, token(roles)); await session.login('EMPLOYEE@example.com', 'password'); }

test('login uses actual DTO, normalizes email, preserves password and persists token pair', async () => {
  const { session, saved } = setup();
  session.publicClient.defaults.adapter = async c => { assert.equal(c.url, '/auth/login'); assert.deepEqual(JSON.parse(c.data), { email: 'employee@example.com', password: ' pass ' }); return ok(c, token()); };
  await session.login(' EMPLOYEE@example.com ', ' pass ');
  assert.equal(session.getSnapshot().status, 'authenticated'); assert.deepEqual(JSON.parse(saved()), token());
});
test('fresh app restores through refresh rotation once', async () => {
  const { session, saved } = setup(JSON.stringify(token())); let calls = 0;
  session.publicClient.defaults.adapter = async c => { calls++; assert.equal(c.url, '/auth/refresh'); assert.equal(JSON.parse(c.data).refreshToken, 'refresh-1'); return ok(c, token(undefined, 2)); };
  await Promise.all([session.restore(), session.restore()]); assert.equal(calls, 1); assert.equal(JSON.parse(saved()).refreshToken, 'refresh-2'); assert.equal(session.getSnapshot().status, 'authenticated');
});
test('concurrent 401 share one rotation and retry with new access token', async () => {
  const { session } = setup(); await login(session); let refreshes = 0; const gate = deferred(); const started = deferred();
  session.publicClient.defaults.adapter = async c => { refreshes++; started.resolve(); await gate.promise; return ok(c, token(undefined, 2)); };
  session.client.defaults.adapter = async c => { if (c.headers.get('Authorization') === `Bearer ${token().accessToken}`) return fail(c, 401); assert.equal(c.headers.get('Authorization'), `Bearer ${token(undefined, 2).accessToken}`); return ok(c); };
  const requests = Array.from({ length: 8 }, () => session.client.get('/protected'));
  await started.promise; await new Promise(r => setImmediate(r)); gate.resolve(); await Promise.all(requests); assert.equal(refreshes, 1);
});
test('late old-token 401 reuses completed rotation', async () => {
  const { session } = setup(); await login(session); const late = deferred(), started = deferred(); let refreshes = 0;
  session.publicClient.defaults.adapter = async c => { refreshes++; return ok(c, token(undefined, 2)); };
  session.client.defaults.adapter = async c => {
    if (!c.authRetried) { if (c.url === '/late') { started.resolve(); await late.promise; } return fail(c, 401); }
    return ok(c);
  };
  const pending = session.client.get('/late'); await started.promise; await session.client.get('/first'); late.resolve(); await pending; assert.equal(refreshes, 1);
});
test('duplicate login submits one request', async () => {
  const { session } = setup(); let calls = 0;
  session.publicClient.defaults.adapter = async c => { calls++; return ok(c, token()); };
  await Promise.all([session.login('a@example.com', 'password'), session.login('a@example.com', 'password')]); assert.equal(calls, 1);
});
test('invalid access token never enters employee routes', async () => {
  const { session, saved } = setup(); session.publicClient.defaults.adapter = async c => ok(c, { ...token(), accessToken: 'invalid' });
  await assert.rejects(session.login('a@example.com', 'password')); assert.equal(session.getSnapshot().status, 'anonymous'); assert.equal(saved(), null);
});
test('original request retries at most once', async () => {
  const { session, saved } = setup(); await login(session); let requests = 0, refreshes = 0;
  session.publicClient.defaults.adapter = async c => { refreshes++; return ok(c, token(undefined, 2)); };
  session.client.defaults.adapter = async c => { requests++; return fail(c, 401); };
  await assert.rejects(session.client.get('/protected')); assert.equal(requests, 2); assert.equal(refreshes, 1); assert.equal(saved(), null); assert.equal(session.getSnapshot().status, 'anonymous');
});
for (const status of [401, 500]) test(`refresh failure ${status} clears session`, async () => {
  const { session, saved } = setup(); await login(session);
  session.publicClient.defaults.adapter = async c => fail(c, status); session.client.defaults.adapter = async c => fail(c, 401);
  await assert.rejects(session.client.get('/protected')); assert.equal(saved(), null); assert.equal(session.getSnapshot().status, 'anonymous');
});
for (const roles of [['ADMIN'], ['INSTRUCTOR'], ['EMPLOYEE', 'ADMIN']]) test(`role gate ${roles.join('+')}`, async () => {
  const { session, saved } = setup();
  if (roles.includes('EMPLOYEE')) { await login(session, roles); assert.equal(session.getSnapshot().status, 'authenticated'); }
  else { await assert.rejects(login(session, roles), /관리자 웹/); assert.equal(saved(), null); assert.equal(session.getSnapshot().status, 'anonymous'); }
});
test('login rejection including RESIGNED uses sanitized message and no stored tokens', async () => {
  const { session, saved } = setup(); session.publicClient.defaults.adapter = async c => fail(c, 401);
  await assert.rejects(session.login('a@example.com', 'password')); assert.equal(saved(), null); assert.match(session.getSnapshot().message, /이메일과 비밀번호/);
});
test('403 does not refresh or erase valid authentication', async () => {
  const { session } = setup(); await login(session); session.publicClient.defaults.adapter = async () => { assert.fail('unexpected refresh'); };
  session.client.defaults.adapter = async c => fail(c, 403); await assert.rejects(session.client.get('/admin')); assert.equal(session.getSnapshot().status, 'authenticated');
});
test('logout clears locally even when backend unavailable', async () => {
  const { session, saved } = setup(); await login(session); session.publicClient.defaults.adapter = async c => { assert.equal(c.url, '/auth/logout'); assert.ok(c.headers.get('Authorization')); throw new AxiosError('offline'); };
  await session.logout(); assert.equal(saved(), null); assert.equal(session.getSnapshot().status, 'anonymous'); await assert.rejects(session.client.get('/protected'));
});
test('logout during refresh cannot restore stale session', async () => {
  const { session, saved } = setup(); await login(session); const gate = deferred(), started = deferred();
  session.publicClient.defaults.adapter = async c => { if(c.url === '/auth/logout') return ok(c); started.resolve(); await gate.promise; return ok(c, token(undefined, 2)); };
  session.client.defaults.adapter = async c => fail(c, 401);
  const request = session.client.get('/protected'); const rejected = assert.rejects(request); await started.promise; await session.logout(); gate.resolve(); await rejected;
  assert.equal(saved(), null); assert.equal(session.getSnapshot().status, 'anonymous');
});
test('logout during login cleanup cancels login before network request', async () => {
  const { session, storage } = setup(); const gate = deferred(), started = deferred(); let removes = 0;
  storage.remove = async () => { if (++removes === 1) { started.resolve(); await gate.promise; } };
  session.publicClient.defaults.adapter = async () => assert.fail('cancelled login sent');
  const pending = assert.rejects(session.login('a@example.com', 'password')); await started.promise; const logout = session.logout(); gate.resolve(); await Promise.all([pending, logout]); assert.equal(session.getSnapshot().status, 'anonymous');
});
for (const initial of [null, '{broken', JSON.stringify({ refreshToken: '' })]) test(`restore invalid/absent storage ${initial}`, async () => {
  const { session } = setup(initial); await session.restore(); assert.equal(session.getSnapshot().status, 'anonymous');
});
test('offline startup stops loading and clears tokens', async () => {
  const { session, saved } = setup(JSON.stringify(token())); session.publicClient.defaults.adapter = async () => { throw new AxiosError('offline'); };
  await session.restore(); assert.equal(session.getSnapshot().status, 'anonymous'); assert.equal(saved(), null);
});
test('secure storage write failure fails closed', async () => {
  const { session, storage } = setup(); storage.write = async () => { throw new Error('native error'); }; await assert.rejects(login(session), /저장소/); assert.equal(session.getSnapshot().status, 'anonymous');
});
test('failed secure delete overwrites with empty tombstone', async () => {
  const { session, storage, saved } = setup(); await login(session); storage.remove = async () => { throw new Error('delete error'); }; await session.logout(); assert.equal(saved(), '');
});
test('validation and native API configuration', () => {
  assert.ok(loginValidation('bad', 'password')); assert.ok(loginValidation('a@example.com', ' ')); assert.equal(loginValidation('a@example.com', 'password'), '');
  assert.throws(() => apiAddress(undefined)); assert.throws(() => apiAddress('http://localhost:8080/api')); assert.throws(() => apiAddress('http://127.0.0.1:8080/api')); assert.throws(() => apiAddress('https://user:pass@example.com'));
  assert.equal(apiAddress('http://192.168.0.9:8080/api/'), 'http://192.168.0.9:8080/api');
});
