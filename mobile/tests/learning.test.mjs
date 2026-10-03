import { test } from 'node:test';
import assert from 'node:assert/strict';
import { create, AxiosError } from 'axios';
import { createLearningApi, editable, learningError, openContentUrl, parseProgress, rateLabel, routeId, textValue } from '../src/learning/api.ts';
import { createResource } from '../src/learning/resource.ts';
import { createAuthSession } from '../src/auth/session.ts';
const response = (config, data) => ({ config, data, status: 200, statusText: 'OK', headers: {} });
const failure = (config, status) => new AxiosError('failure', 'ERR_BAD_RESPONSE', config, null, { ...response(config, {}), status });
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
const enrollment = { enrollmentId: 8, memberId: 1, memberName: '직원', courseId: 3, courseTitle: '긴 교육 제목', courseType: 'MANDATORY', courseStartDate: '2026-01-01', courseEndDate: '2026-12-31', status: 'ASSIGNED', assignmentSource: 'AUTOMATIC', assignmentRuleId: 1, assignedAt: '2026-01-01T00:00:00', startedAt: null, completedAt: null, dueDate: '2026-12-31' };
const content = { contentId: 10, title: '콘텐츠', contentType: 'VIDEO', contentUrl: 'https://example.com/video', durationSeconds: 90, required: true, sortOrder: 1, progressRate: 0, completedAt: null };
const detail = { ...enrollment, courseDescription: null, instructorId: null, instructorName: null, progressRate: 0, passingProgressRate: 80, contentConditionSatisfied: false, contents: [content], exam: null };
const page = (items = [enrollment]) => ({ content: items, page: 0, size: 10, totalElements: items.length, totalPages: items.length ? 1 : 0 });

for (const status of [undefined, 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'EXPIRED']) test(`server list filter ${status ?? 'ALL'} and pagination`, async () => {
  const client = create(); let calls = 0;
  client.defaults.adapter = async c => { calls++; if(c.url === '/enrollments/me') { assert.deepEqual(c.params, { status, page: 2, size: 10 }); return response(c, page()); } assert.equal(c.url, '/enrollments/8/progress'); return response(c, { ...detail, progressRate: 32 }); };
  const result = await createLearningApi(client).list(status, 2); assert.equal(result.content[0].progressRate, 32); assert.equal(calls, 2);
});
test('empty list does not request progress', async () => {
  const client = create(); let calls = 0; client.defaults.adapter = async c => { calls++; return response(c, page([])); };
  assert.deepEqual((await createLearningApi(client).list()).content, []); assert.equal(calls, 1);
});
test('progress enrichment concurrency bounded to three; partial errors remain visible', async () => {
  const client = create(); let active = 0, maximum = 0;
  client.defaults.adapter = async c => {
    if (c.url === '/enrollments/me') return response(c, page(Array.from({ length: 10 }, (_, i) => ({ ...enrollment, enrollmentId: i + 1 }))));
    active++; maximum = Math.max(active, maximum); await new Promise(r => setImmediate(r)); active--;
    if(c.url === '/enrollments/2/progress') throw failure(c, 500); return response(c, detail);
  };
  const result = await createLearningApi(client).list(); assert.equal(maximum, 3); assert.equal(result.content[1].progressUnavailable, true); assert.equal(result.content[1].progressRate, null); assert.equal(result.content[0].progressUnavailable, false);
});
test('detail exposes server content metadata and exam only', async () => {
  const client = create(); client.defaults.adapter = async c => { assert.equal(c.url, '/enrollments/8/progress'); return response(c, detail); };
  const result = await createLearningApi(client).detail(8); assert.deepEqual(result.contents, [content]); assert.equal(result.exam, null);
});
test('ownership denial becomes error and clears old data', async () => {
  const client = create(); client.defaults.adapter = async c => { throw failure(c, 403); };
  const resource = createResource(learningError); await resource.run(() => Promise.resolve(detail)); await resource.run(signal => createLearningApi(client).detail(9, signal));
  assert.equal(resource.snapshot().data, null); assert.match(resource.snapshot().error, /본인/); assert.equal(resource.snapshot().loading, false);
});
for (const status of ['IN_PROGRESS', 'COMPLETED']) test(`progress PATCH adopts server ${status} without local completion calculation`, async () => {
  const client = create(); const server = { ...detail, status, progressRate: 64, contents: [{ ...content, progressRate: 100 }] };
  client.defaults.adapter = async c => { assert.equal(c.method, 'patch'); assert.equal(c.url, '/enrollments/8/contents/10/progress'); assert.deepEqual(JSON.parse(c.data), { progressRate: 100 }); return response(c, server); };
  const resource = createResource(learningError); await resource.run(signal => createLearningApi(client).save(8, 10, 100, signal)); assert.deepEqual(resource.snapshot().data, server);
});
test('only active enrollment permits editing', () => {
  assert.equal(editable('ASSIGNED'), true); assert.equal(editable('IN_PROGRESS'), true);
  for (const status of ['COMPLETED', 'FAILED', 'EXPIRED']) assert.equal(editable(status), false);
});
test('progress validation and safe empty display', () => {
  for (const input of ['', '-1', '101', '1.234', 'NaN', 'Infinity', '1e2']) assert.equal(parseProgress(input), null);
  assert.equal(parseProgress('25.25'), 25.25); assert.equal(parseProgress('100'), 100); assert.equal(parseProgress('0'), 0);
  for (const value of [undefined, null, NaN, Infinity]) assert.equal(rateLabel(value), '—'); assert.equal(textValue(null), '—'); assert.equal(rateLabel(0), '0%');
  for (const value of [undefined, ['1'], '0', '-1', '1.2', '9007199254740992']) assert.equal(routeId(value), null); assert.equal(routeId('8'), 8);
});
test('external URLs allow http/https and handle native errors', async () => {
  for (const value of ['javascript:alert(1)', 'file:///private', 'intent://test', 'broken', 'https://user:password@example.com']) assert.match(await openContentUrl(value, () => assert.fail('unsafe URL opened')), /주소/);
  let opened; assert.equal(await openContentUrl('https://example.com/video', async url => { opened = url; }), ''); assert.equal(opened, 'https://example.com/video');
  assert.match(await openContentUrl('https://example.com', async () => { throw new Error('native'); }), /열 수 없습니다/);
});
test('pull-to-refresh and repeated retry share pending request; successful retry resets errors', async () => {
  const resource = createResource(learningError), gate = deferred(); let calls = 0;
  const load = async () => { calls++; await gate.promise; return detail; };
  const first = resource.run(load), second = resource.run(load); assert.equal(first, second); assert.equal(resource.snapshot().loading, true); gate.resolve(); await first; assert.equal(calls, 1);
  await resource.run(async () => { throw new Error('offline'); }); assert.ok(resource.snapshot().error); assert.equal(resource.snapshot().loading, false);
  await resource.run(async () => detail); assert.equal(resource.snapshot().error, ''); assert.equal(resource.snapshot().data, detail);
});
test('filter change/unmount aborts old request and rejects stale response overwrite', async () => {
  const resource = createResource(learningError), gate = deferred(); let oldSignal;
  const old = resource.run(async signal => { oldSignal = signal; await gate.promise; return { stale: true }; }); await Promise.resolve(); resource.cancel(); assert.equal(oldSignal.aborted, true);
  await resource.run(async () => ({ fresh: true })); gate.resolve(); await old; assert.deepEqual(resource.snapshot().data, { fresh: true });
});
test('401 refresh rotation restores learning resource response', async () => {
  let stored; const session = createAuthSession({ read: async () => stored ?? null, write: async v => { stored = v; }, remove: async () => { stored = null; } }, 'https://example.com/api');
  const tokens = n => ({ accessToken: `a.${Buffer.from(JSON.stringify({ memberId: 1, email: 'e@example.com', roles: ['EMPLOYEE'], tokenType: 'ACCESS', exp: 9999999999, n })).toString('base64url')}.b`, refreshToken: `refresh-${n}`, tokenType: 'Bearer', accessTokenExpiresInSeconds: 3600, refreshTokenExpiresInSeconds: 86400 });
  let refreshes = 0; session.publicClient.defaults.adapter = async c => { if(c.url === '/auth/refresh') { refreshes++; return response(c, tokens(2)); } return response(c, tokens(1)); };
  await session.login('e@example.com', 'password'); session.client.defaults.adapter = async c => { if (!c.authRetried) throw failure(c, 401); return response(c, detail); };
  const resource = createResource(learningError); await resource.run(signal => createLearningApi(session.client).detail(8, signal));
  assert.equal(resource.snapshot().data.status, 'ASSIGNED'); assert.equal(resource.snapshot().error, ''); assert.equal(resource.snapshot().loading, false); assert.equal(refreshes, 1); assert.equal(JSON.parse(stored).refreshToken, 'refresh-2');
});
