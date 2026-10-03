import { test } from 'node:test';
import assert from 'node:assert/strict';
import { create, AxiosError } from 'axios';
import { createCourseworkApi, submitAndReload, maySubmit, validateSubmission, contentLimit, gradeTime, statusLabels } from '../src/coursework/api.ts';
import { createResource } from '../src/learning/resource.ts';
import { learningError, textValue, openContentUrl } from '../src/learning/api.ts';
import { createAuthSession } from '../src/auth/session.ts';
const ok = (config, data) => ({ config, data, status: 200, statusText: 'OK', headers: {} });
const fail = (config, status, code) => new AxiosError('failed', '', config, null, { ...ok(config, { code }), status });
const item = { assignment: { assignmentId: 3, courseId: 4, title: '과제', description: '설명', required: true, dueDate: '2026-10-30', passingScore: 70, sortOrder: 1, createdAt: '', updatedAt: '' }, effectiveDueDate: '2026-10-20', status: 'NOT_SUBMITTED', submittable: true, resubmittable: false, submission: null };
const submission = { submissionId: 9, assignmentId: 3, enrollmentId: 7, memberId: 1, memberName: '직원', submissionType: 'TEXT', content: '내용', submittedAt: '2026-10-03T00:00:00', submissionCount: 1, status: 'PENDING_GRADING', score: null, passed: null, feedback: null, gradedAt: null, gradedByMemberId: null, gradedByName: null, version: 0 };
const pending = { ...item, status: 'PENDING_GRADING', resubmittable: true, submission };
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };

test('employee list empty state uses actual list API', async () => {
  const client = create(); client.defaults.adapter = async c => { assert.equal(c.url, '/enrollments/7/assignments'); return ok(c, []); };
  assert.deepEqual(await createCourseworkApi(client).list(7), []);
});
test('list preserves required/optional, description and server effective deadline', async () => {
  const rows = [item, { ...item, assignment: { ...item.assignment, assignmentId: 5, required: false } }];
  const client = create(); client.defaults.adapter = async c => ok(c, rows); assert.deepEqual(await createCourseworkApi(client).list(7), rows);
});
for (const type of ['TEXT', 'URL']) test(`${type} submission matches contract and reloads canonical result`, async () => {
  const client = create(); const calls = []; const content = type === 'TEXT' ? '작성한 내용' : 'https://example.com/work';
  const result = { ...pending, submission: { ...submission, submissionType: type, content } };
  client.defaults.adapter = async c => {
    calls.push(c.method);
    if(c.method === 'put') { assert.equal(c.url, '/enrollments/7/assignments/3/submission'); assert.deepEqual(JSON.parse(c.data), { submissionType: type, content }); return ok(c, result); }
    if(c.url.endsWith('/progress')) return ok(c, { status: 'IN_PROGRESS' });
    assert.equal(c.url, '/enrollments/7/assignments/3'); return ok(c, result);
  };
  const loaded = await submitAndReload(createCourseworkApi(client), 7, 3, type, content); assert.deepEqual(loaded.item, result); assert.equal(loaded.enrollment.status, 'IN_PROGRESS'); assert.deepEqual(calls, ['put', 'get', 'get']);
});
test('TEXT and URL limits reflect backend and blank content rejected', () => {
  assert.equal(contentLimit('TEXT'), 10000); assert.equal(contentLimit('URL'), 2048);
  assert.ok(validateSubmission('TEXT', ' \n ')); assert.ok(validateSubmission('URL', ''));
  assert.equal(validateSubmission('TEXT', '가'.repeat(10000)), ''); assert.ok(validateSubmission('TEXT', '가'.repeat(10001)));
  assert.ok(validateSubmission('URL', 'a'.repeat(2049))); assert.equal(validateSubmission('URL', 'https://example.com'), '');
});
for (const status of ['NOT_SUBMITTED', 'PENDING_GRADING', 'PASSED', 'FAILED']) test(`status ${status} displays without enrollment inference`, () => {
  assert.ok(statusLabels[status]);
  const value = { ...item, status, submittable: false, resubmittable: false, submission: status === 'NOT_SUBMITTED' ? null : submission };
  assert.equal(maySubmit(value), false);
});
test('pending and failed resubmit use server flag rather than local date rules', () => {
  for(const status of ['PENDING_GRADING', 'FAILED']) {
    assert.equal(maySubmit({ ...pending, status, effectiveDueDate: '2000-01-01' }), true);
    assert.equal(maySubmit({ ...pending, status, resubmittable: false, effectiveDueDate: '2099-01-01' }), false);
  }
  assert.equal(maySubmit(item), true);
  assert.equal(maySubmit({ ...item, submittable: false }), false);
  assert.equal(maySubmit({ ...pending, status: 'PASSED', submittable: false, resubmittable: false }), false);
});
test('resubmission reflects cleared grading and incremented count from server', async () => {
  const result = { ...pending, submission: { ...submission, submissionCount: 2, version: 2 } };
  const client = create(); client.defaults.adapter = async c => ok(c, c.url.endsWith('/progress') ? { status: 'IN_PROGRESS' } : result);
  const loaded = await submitAndReload(createCourseworkApi(client), 7, 3, 'TEXT', '수정');
  assert.equal(loaded.item.submission.submissionCount, 2);
  for (const key of ['score', 'passed', 'feedback', 'gradedAt']) assert.equal(loaded.item.submission[key], null);
});
for (const passed of [true, false]) test(`grade ${passed ? 'PASS' : 'FAIL'} reloads independent enrollment state`, async () => {
  const row = { ...pending, status: passed ? 'PASSED' : 'FAILED', submission: { ...submission, passed, score: passed ? 90 : 30, feedback: '피드백', gradedAt: '2026-10-03T00:00:00' } };
  const client = create(); client.defaults.adapter = async c => ok(c, c.url.endsWith('/progress') ? { status: passed ? 'COMPLETED' : 'IN_PROGRESS' } : row);
  const loaded = await createCourseworkApi(client).load(7, 3); assert.equal(loaded.item.submission.passed, passed); assert.equal(loaded.enrollment.status, passed ? 'COMPLETED' : 'IN_PROGRESS');
});
test('null feedback/grade timestamp safe; UTC timestamps display in Seoul', () => {
  assert.equal(textValue(submission.feedback), '—'); assert.equal(gradeTime(null), '—'); assert.equal(gradeTime('invalid'), '—'); assert.match(gradeTime('2026-10-03T00:00:00'), /9/);
});
test('unsafe submitted URL cannot invoke native opener', async () => {
  assert.ok(await openContentUrl('javascript:alert(1)', async () => assert.fail('unsafe')));
  assert.equal(await openContentUrl('https://example.com', async () => {}), '');
});
test('ownership rejection and loading/error/retry restore resource state', async () => {
  const client = create(); client.defaults.adapter = async c => { throw fail(c, 403); };
  const resource = createResource(learningError), api = createCourseworkApi(client);
  const loading = resource.run(signal => api.load(7, 3, signal)); assert.equal(resource.snapshot().loading, true); await loading;
  assert.equal(resource.snapshot().data, null); assert.equal(resource.snapshot().loading, false); assert.match(resource.snapshot().error, /본인/);
  client.defaults.adapter = async c => ok(c, c.url.endsWith('/progress') ? { status: 'ASSIGNED' } : item);
  await resource.run(signal => api.load(7, 3, signal)); assert.equal(resource.snapshot().error, ''); assert.equal(resource.snapshot().data.item.status, 'NOT_SUBMITTED');
});
test('duplicate submit clicks share pending operation', async () => {
  const client = create(), gate = deferred(); let submissions = 0;
  client.defaults.adapter = async c => { if(c.method === 'put') { submissions++; await gate.promise; } return ok(c, c.url.endsWith('/progress') ? { status: 'IN_PROGRESS' } : pending); };
  const api = createCourseworkApi(client), resource = createResource(learningError), save = signal => submitAndReload(api, 7, 3, 'TEXT', '내용', signal);
  const a = resource.run(save), b = resource.run(save); assert.equal(a, b); gate.resolve(); await a; assert.equal(submissions, 1);
});
test('lost PUT response reconciles by GET without resubmitting', async () => {
  const client = create(); let writes = 0;
  client.defaults.adapter = async c => { if(c.method === 'put') { writes++; throw new AxiosError('offline'); } return ok(c, c.url.endsWith('/progress') ? { status: 'IN_PROGRESS' } : pending); };
  const result = await submitAndReload(createCourseworkApi(client), 7, 3, 'TEXT', '내용'); assert.equal(writes, 1); assert.equal(result.item.submission.submissionCount, 1); assert.match(result.notice, /제출 결과/);
});
test('server deadline rejection reloads disabled action', async () => {
  const client = create(); client.defaults.adapter = async c => { if(c.method === 'put') throw fail(c, 409, 'ASSIGNMENT_DEADLINE_PASSED'); return ok(c, c.url.endsWith('/progress') ? { status: 'IN_PROGRESS' } : { ...item, submittable: false }); };
  const result = await submitAndReload(createCourseworkApi(client), 7, 3, 'TEXT', '내용'); assert.equal(maySubmit(result.item), false); assert.match(result.notice, /마감일/);
});
test('stale refresh cannot replace newer assignment state', async () => {
  const resource = createResource(learningError), gate = deferred();
  const old = resource.run(async () => { await gate.promise; return item; }); await Promise.resolve(); resource.cancel();
  await resource.run(async () => pending); gate.resolve(); await old; assert.equal(resource.snapshot().data.status, 'PENDING_GRADING');
});
test('401 refresh preserves coursework submit and body', async () => {
  const auth = createAuthSession({ read: async () => null, write: async () => {}, remove: async () => {} }, 'https://example.com/api');
  const tokens = { accessToken: `a.${Buffer.from(JSON.stringify({ memberId: 1, email: 'a@example.com', roles: ['EMPLOYEE'], tokenType: 'ACCESS', exp: 9999999999 })).toString('base64url')}.b`, refreshToken: 'refresh', tokenType: 'Bearer', accessTokenExpiresInSeconds: 100, refreshTokenExpiresInSeconds: 100 };
  let refreshes = 0; auth.publicClient.defaults.adapter = async c => { if(c.url.endsWith('/refresh')) refreshes++; return ok(c, tokens); }; await auth.login('a@example.com', 'password');
  auth.client.defaults.adapter = async c => { if(!c.authRetried) throw fail(c, 401); assert.deepEqual(JSON.parse(c.data), { submissionType: 'TEXT', content: '내용' }); return ok(c, pending); };
  assert.equal((await createCourseworkApi(auth.client).submit(7, 3, 'TEXT', '내용')).status, 'PENDING_GRADING'); assert.equal(refreshes, 1);
});
