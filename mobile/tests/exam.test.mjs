import { test } from 'node:test';
import assert from 'node:assert/strict';
import { create, AxiosError } from 'axios';
import { createAttemptStart, createExamApi, selectChoice } from '../src/exam/api.ts';
import { createAnswerSession } from '../src/exam/session.ts';
import { createAuthSession } from '../src/auth/session.ts';
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
const ok = (config, data) => ({ config, data, status: 200, statusText: 'OK', headers: {} });
const attempt = { attemptId: 5, attemptNumber: 1, score: null, passed: null, startedAt: '2026-10-03T00:00:00', submittedAt: null, maxAttempts: 3, remainingAttempts: 2 };
const eligibility = { canStart: false, canContinue: true, passed: false, attemptCount: 1, maxAttempts: 3, remainingAttempts: 2, openAttemptId: 5, blockedReason: null };
const paper = { attemptId: 5, title: '시험', questions: [{ questionId: 1, questionType: 'SINGLE_CHOICE', questionText: '문제', score: 10, sortOrder: 1, choices: [{ choiceId: 10, choiceText: 'A', sortOrder: 1 }, { choiceId: 11, choiceText: 'B', sortOrder: 2 }], selectedChoiceIds: [10] }] };
const passed = { ...attempt, submittedAt: '2026-10-03T00:01:00', score: 100, passed: true };
test('leaving detail aborts start and ignores a late successful attempt response', async () => {
  const gate = deferred(), moves = []; let signal;
  const start = createAttemptStart(async (_, requestSignal) => { signal = requestSignal; await gate.promise; return attempt; });
  const pending = start.run(7, result => moves.push(result.attemptId));
  start.cancel(); assert.equal(signal.aborted, true); gate.resolve(); await pending; assert.deepEqual(moves, []);
});
test('cancelled old start cannot navigate after returning and starting again', async () => {
  const gate = deferred(), moves = []; let calls = 0;
  const start = createAttemptStart(async () => { if (++calls === 1) { await gate.promise; return attempt; } return { ...attempt, attemptId: 6 }; });
  const old = start.run(7, result => moves.push(result.attemptId)); start.cancel();
  await start.run(7, result => moves.push(result.attemptId)); gate.resolve(); await old; assert.deepEqual(moves, [6]);
});
test('start suppresses duplicate requests and keeps real errors retryable', async () => {
  const gate = deferred(); let calls = 0;
  const start = createAttemptStart(async () => { calls++; await gate.promise; throw Error('offline'); });
  const first = start.run(7, () => {}), duplicate = start.run(7, () => {});
  assert.equal(first, duplicate); gate.resolve(); await assert.rejects(first, /offline/); assert.equal(calls, 1);
  await assert.rejects(start.run(7, () => {}), /offline/); assert.equal(calls, 2);
});
test('attempt start forwards cancellation to the shared Axios client', async () => {
  const client = create(), controller = new AbortController();
  client.defaults.adapter = async config => { assert.equal(config.signal, controller.signal); return ok(config, attempt); };
  await createExamApi(client).start(7, controller.signal);
});
for (const type of ['SINGLE_CHOICE', 'TRUE_FALSE']) test(`${type} selects exactly one`, () => { assert.deepEqual(selectChoice(type, [10], 11), [11]); assert.deepEqual(selectChoice(type, [10], 10), [10]); });
test('MULTIPLE_CHOICE toggles and permits clearing all choices', () => { assert.deepEqual(selectChoice('MULTIPLE_CHOICE', [10], 11), [10, 11]); assert.deepEqual(selectChoice('MULTIPLE_CHOICE', [10], 10), []); });
test('new and reused attempts use same POST contract', async () => {
  const client = create(); client.defaults.adapter = async c => { assert.equal(c.url, '/enrollments/7/exam-attempts'); assert.equal(c.method, 'post'); return ok(c, attempt); };
  const api = createExamApi(client); assert.deepEqual(await api.start(7), attempt); assert.deepEqual(await api.start(7), attempt);
});
test('load validates enrollment membership and restores saved paper', async () => {
  const client = create(); client.defaults.adapter = async c => ok(c, c.url.endsWith('/exam-attempts') ? [attempt] : c.url.endsWith('/exam-eligibility') ? eligibility : c.url.endsWith('/progress') ? { status: 'IN_PROGRESS' } : paper);
  const api = createExamApi(client), loaded = await api.load(7, 5); assert.equal(loaded.paper.questions[0].selectedChoiceIds[0], 10);
  await assert.rejects(api.load(7, 9), /해당하는 응시/);
});
for (const reason of ['EXAM_NOT_FOUND', 'EXAM_ALREADY_PASSED', 'EXAM_ATTEMPTS_EXHAUSTED', 'ENROLLMENT_EXAM_NOT_EDITABLE']) test(`server denied eligibility ${reason} never loads editable paper`, async () => {
  const client = create(); client.defaults.adapter = async c => {
    if(c.url.endsWith('/exam-attempts')) return ok(c, [attempt]);
    if(c.url.endsWith('/exam-eligibility')) return ok(c, { ...eligibility, canContinue: false, canStart: false, blockedReason: reason });
    if(c.url.endsWith('/progress')) return ok(c, { status: 'FAILED' });
    assert.fail('paper should not be requested');
  };
  assert.equal((await createExamApi(client).load(7, 5)).paper, null);
});
test('rapid answer saves are serialized and old responses do not overwrite local selection', async () => {
  const gate = deferred(); const calls = []; const session = createAnswerSession(paper, { save: async (id, ids) => { calls.push([...ids]); if(calls.length === 1) await gate.promise; return { selectedChoiceIds: ids }; }, submit: async () => passed });
  session.change(1, [11]); session.change(1, [10]); session.change(1, [11]); await Promise.resolve(); assert.equal(calls.length, 1); assert.deepEqual(session.snapshot().answers[1], [11]); gate.resolve(); await session.flush(); assert.deepEqual(calls, [[11], [10], [11]]); assert.deepEqual(session.snapshot().answers[1], [11]);
});
test('submission waits for pending writes and is single flight', async () => {
  const gate = deferred(); const calls = []; const session = createAnswerSession(paper, { save: async () => { calls.push('save'); await gate.promise; }, submit: async () => { calls.push('submit'); return passed; } });
  session.change(1, [11]); const first = session.submit(), second = session.submit(); assert.equal(first, second); session.change(1, [10]); await Promise.resolve(); assert.deepEqual(calls, ['save']); gate.resolve(); assert.deepEqual(await first, passed); assert.deepEqual(calls, ['save', 'submit']); assert.equal(session.snapshot().submitted, true); assert.deepEqual(session.snapshot().answers[1], [11]);
});
test('ambiguous save stops queue and blocks submission until server restore', async () => {
  let calls = 0; const session = createAnswerSession(paper, { save: async () => { calls++; throw new Error('offline'); }, submit: async () => assert.fail('unsafe submit') });
  session.change(1, [11]); session.change(1, [10]); await assert.rejects(session.flush()); assert.equal(calls, 1); assert.equal(session.snapshot().pending, 0); assert.equal(session.snapshot().uncertain, true); await assert.rejects(session.submit());
  const restored = createAnswerSession(paper, { save: async () => {}, submit: async () => passed }); assert.deepEqual(restored.snapshot().answers[1], [10]); assert.equal(restored.snapshot().uncertain, false);
});
test('leave flush retains saved answers without automatic submission', async () => {
  let saved; const session = createAnswerSession(paper, { save: async (id, ids) => { saved = ids; }, submit: async () => assert.fail('auto submit') }); session.change(1, [11]); await session.flush();
  const restored = createAnswerSession({ ...paper, questions: [{ ...paper.questions[0], selectedChoiceIds: saved }] }, { save: async () => {}, submit: async () => passed }); assert.deepEqual(restored.snapshot().answers[1], [11]);
});
for (const [success, status, remaining] of [[true, 'COMPLETED', 2], [false, 'IN_PROGRESS', 1], [false, 'FAILED', 0]]) test(`server result ${status} and eligibility used without grading`, async () => {
  const client = create(); const result = { ...passed, passed: success, score: success ? 100 : 0, remainingAttempts: remaining };
  client.defaults.adapter = async c => {
    if(c.url.endsWith('/exam-attempts')) return ok(c, [result]);
    if(c.url.endsWith('/exam-eligibility')) return ok(c, { ...eligibility, canContinue: false, canStart: !success && remaining > 0 });
    if(c.url.endsWith('/progress')) return ok(c, { status });
    assert.equal(c.url, '/exam-attempts/5/result'); return ok(c, result);
  };
  const loaded = await createExamApi(client).load(7, 5); assert.equal(loaded.detail.status, status); assert.equal(loaded.attempt.passed, success); assert.equal(loaded.paper, null); assert.equal(loaded.access.canStart, !success && remaining > 0);
});
test('lost submission response recovers through submitted history', async () => {
  const session = createAnswerSession(paper, { save: async () => {}, submit: async () => { throw new Error('response lost'); } }); await assert.rejects(session.submit()); assert.equal(session.snapshot().uncertain, true); assert.equal(session.snapshot().submitting, false);
});
test('answer HTTP DTO and 401 refresh preserves save', async () => {
  const session = createAuthSession({ read: async () => null, write: async () => {}, remove: async () => {} }, 'https://example.com/api');
  const token = n => ({ accessToken: `a.${Buffer.from(JSON.stringify({ memberId: 1, email: 'a@example.com', roles: ['EMPLOYEE'], tokenType: 'ACCESS', exp: 9999999999, n })).toString('base64url')}.b`, refreshToken: `r${n}`, tokenType: 'Bearer', accessTokenExpiresInSeconds: 100, refreshTokenExpiresInSeconds: 100 });
  let refreshes = 0; session.publicClient.defaults.adapter = async c => { if(c.url.endsWith('/refresh')) refreshes++; return ok(c, token(refreshes)); }; await session.login('a@example.com', 'password');
  session.client.defaults.adapter = async c => { if(!c.authRetried) throw new AxiosError('expired', '', c, null, { ...ok(c, {}), status: 401 }); assert.equal(c.url, '/exam-attempts/5/answers/1'); assert.deepEqual(JSON.parse(c.data), { selectedChoiceIds: [11] }); return ok(c, { questionId: 1, selectedChoiceIds: [11] }); };
  assert.deepEqual(await createExamApi(session.client).save(5, 1, [11]), { questionId: 1, selectedChoiceIds: [11] }); assert.equal(refreshes, 1);
});
