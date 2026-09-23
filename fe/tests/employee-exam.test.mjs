import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { createServer } from 'vite'
import { AxiosError } from 'axios'
import { apiClient, publicClient } from '../src/api/client.ts'
import { authSession } from '../src/auth/authSession.ts'
import { authStorage } from '../src/auth/authStorage.ts'
import { employeeExamApi } from '../src/api/employeeExamApi.ts'
import { createAnswerSession } from '../src/pages/employee/answerSession.ts'
import { examAction, examErrorMessage, scoreLabel, selectChoice, submissionDescription } from '../src/pages/employee/employeeExamUtils.ts'

const attempt = { attemptId: 71, attemptNumber: 2, score: null, passed: null, startedAt: '2026-09-23T00:00:00', submittedAt: null, maxAttempts: 3, remainingAttempts: 1 }
const result = { ...attempt, score: 92.50, passed: true, submittedAt: '2026-09-23T01:00:00' }
const detail = { enrollmentId: 10, status: 'IN_PROGRESS', exam: { examId: 5, title: '최종 평가', passingScore: 80, maxAttempts: 3 } }
const paper = { attemptId: 71, title: '최종 평가', questions: [
  { questionId: 90, questionType: 'SINGLE_CHOICE', questionText: '단일 문항', score: 20, sortOrder: 2, choices: [{ choiceId: 82, choiceText: '선택 나', sortOrder: 2 }, { choiceId: 83, choiceText: '선택 가', sortOrder: 1 }], selectedChoiceIds: [83] },
  { questionId: 91, questionType: 'MULTIPLE_CHOICE', questionText: '복수 문항', score: 30, sortOrder: 1, choices: [{ choiceId: 95, choiceText: '자료 A', sortOrder: 1 }, { choiceId: 96, choiceText: '자료 B', sortOrder: 2 }], selectedChoiceIds: [] },
  { questionId: 92, questionType: 'TRUE_FALSE', questionText: '참거짓 문항', score: 50, sortOrder: 3, choices: [{ choiceId: 100, choiceText: 'O', sortOrder: 1 }, { choiceId: 101, choiceText: 'X', sortOrder: 2 }], selectedChoiceIds: [101] },
] }
function capture(data) {
  const calls = []
  apiClient.defaults.adapter = async config => { calls.push(config); return { config, data, status: 200, statusText: '', headers: {} } }
  return calls
}
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function conflict() { return new AxiosError('private trace', 'CONFLICT', undefined, undefined, { status: 409, data: { code: 'EXAM_ATTEMPT_ALREADY_SUBMITTED', message: 'private trace' }, headers: {}, config: {} }) }

// 실제 Axios interceptor + 시험 API + 저장 큐를 연결한다. 서버와 저장소만 대체한다.
for (const refreshSucceeds of [true, false]) {
  await test(`시험 답안 저장 중 Refresh ${refreshSucceeds ? '성공 시 회전 후 저장·제출' : '실패 시 인증 정리·제출 차단'}`, async () => {
    const storage = new Map()
    const previousStorage = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage')
    Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, value: {
      getItem: key => storage.get(key) ?? null,
      setItem: (key, value) => storage.set(key, value),
      removeItem: key => storage.delete(key),
    } })
    const expiresAt = Math.floor(Date.now() / 1000) + 3600
    const pair = suffix => ({
      accessToken: `test.${Buffer.from(JSON.stringify({ tokenType: 'ACCESS', memberId: 1, email: 'test@example.com', roles: ['EMPLOYEE'], exp: expiresAt, suffix })).toString('base64url')}.test`,
      refreshToken: `test-refresh-${suffix}`, tokenType: 'Bearer', accessTokenExpiresInSeconds: 3600, refreshTokenExpiresInSeconds: 7200,
    })
    const events = []
    const oldApiAdapter = apiClient.defaults.adapter, oldPublicAdapter = publicClient.defaults.adapter
    try {
      authSession.clear()
      authSession.accept(pair('old'), authSession.getGeneration())
      publicClient.defaults.adapter = async config => {
        events.push('refresh')
        assert.equal(config.url, '/auth/refresh')
        assert.equal(JSON.parse(config.data).refreshToken, 'test-refresh-old')
        if (!refreshSucceeds) throw new AxiosError('expired', undefined, config, undefined, { config, status: 401, data: {}, headers: {} })
        return { config, status: 200, data: pair('new'), headers: {}, statusText: '' }
      }
      apiClient.defaults.adapter = async config => {
        if (config.method === 'put') {
          assert.equal(config.url, '/exam-attempts/71/answers/91')
          assert.deepEqual(JSON.parse(config.data), { selectedChoiceIds: [95, 96] })
          events.push(config._retry ? 'saved' : 'expired-save')
          if (!config._retry) throw new AxiosError('expired', undefined, config, undefined, { config, status: 401, data: {}, headers: {} })
        } else {
          assert.equal(config.url, '/exam-attempts/71/submit')
          events.push('submit')
        }
        assert.equal(config.headers.get('Authorization'), `Bearer ${pair('new').accessToken}`)
        return { config, status: 200, data: result, headers: {}, statusText: '' }
      }
      const session = createAnswerSession(paper, {
        save: (id, ids) => employeeExamApi.saveAnswer(71, id, ids),
        submit: () => employeeExamApi.submit(71),
      })
      session.change(91, [95, 96])
      if (refreshSucceeds) {
        assert.equal((await session.submit()).attemptId, 71)
        assert.deepEqual(events, ['expired-save', 'refresh', 'saved', 'submit'])
        assert.deepEqual(session.getSnapshot().answers[91], [95, 96])
        assert.equal(authStorage.read().refreshToken, 'test-refresh-new')
      } else {
        await assert.rejects(session.submit())
        assert.deepEqual(events, ['expired-save', 'refresh'])
        assert.equal(authStorage.read(), null)
        assert.equal(authSession.getSnapshot().user, null)
      }
    } finally {
      authSession.clear()
      apiClient.defaults.adapter = oldApiAdapter
      publicClient.defaults.adapter = oldPublicAdapter
      if (previousStorage) Object.defineProperty(globalThis, 'sessionStorage', previousStorage)
      else delete globalThis.sessionStorage
    }
  })
}

await test('직원 시험 6개 API는 기존 Endpoint와 선택 ID Payload만 사용', async () => {
  const calls = capture(attempt)
  const started = await employeeExamApi.start(10)
  assert.equal(started.attemptNumber, 2)
  assert.equal(started.attemptId, 71)
  await employeeExamApi.history(10)
  await employeeExamApi.paper(71)
  await employeeExamApi.saveAnswer(71, 91, [95, 96])
  await employeeExamApi.submit(71)
  await employeeExamApi.result(71)
  assert.deepEqual(calls.map(c => [c.method, c.url]), [
    ['post', '/enrollments/10/exam-attempts'], ['get', '/enrollments/10/exam-attempts'], ['get', '/exam-attempts/71'],
    ['put', '/exam-attempts/71/answers/91'], ['post', '/exam-attempts/71/submit'], ['get', '/exam-attempts/71/result'],
  ])
  assert.deepEqual(JSON.parse(calls[3].data), { selectedChoiceIds: [95, 96] })
  assert.equal(calls[0].data, undefined)
  assert.equal(calls[4].data, undefined)
})
await test('시작 응답의 기존 미제출 attemptId를 그대로 재사용', async () => {
  capture(attempt)
  assert.equal((await employeeExamApi.start(10)).attemptId, 71)
  assert.equal((await employeeExamApi.start(10)).attemptId, 71)
})
await test('응시 전·계속하기·재응시·합격·종료 상태 Action', () => {
  assert.equal(examAction('ASSIGNED', [], 3).canStart, true)
  assert.equal(examAction('IN_PROGRESS', [attempt], 3).canContinue, true)
  assert.equal(examAction('IN_PROGRESS', [attempt], 3).canStart, false)
  assert.equal(examAction('IN_PROGRESS', [{ ...result, passed: false }], 3).canStart, true)
  assert.equal(examAction('IN_PROGRESS', [result], 3).canStart, false)
  for (const status of ['FAILED', 'EXPIRED', 'COMPLETED']) {
    assert.equal(examAction(status, [], 3).canStart, false)
    assert.equal(examAction(status, [attempt], 3).canContinue, false)
  }
  assert.equal(examAction('IN_PROGRESS', [{ ...result, passed: false, remainingAttempts: 0 }], 3).canStart, false)
})
await test('SINGLE/TRUE_FALSE는 실제 ID 하나, MULTIPLE은 선택·해제와 빈 집합 허용', () => {
  for (const type of ['SINGLE_CHOICE', 'TRUE_FALSE']) assert.deepEqual(selectChoice(type, [83], 82), [82])
  assert.deepEqual(selectChoice('MULTIPLE_CHOICE', [95], 96), [95, 96])
  assert.deepEqual(selectChoice('MULTIPLE_CHOICE', [95, 96], 95), [96])
  assert.deepEqual(selectChoice('MULTIPLE_CHOICE', [96], 96), [])
})
await test('새 세션은 서버에 저장된 답안을 복원하고 미응답을 유지', () => {
  const session = createAnswerSession(paper, { save: async () => {}, submit: async () => result })
  assert.deepEqual(session.getSnapshot().answers, { 90: [83], 91: [], 92: [101] })
})
await test('빠른 변경을 서버까지 직렬화하고 오래된 응답으로 최신 선택을 덮어쓰지 않음', async () => {
  const first = deferred(), calls = []
  const session = createAnswerSession(paper, { save: async (id, ids) => { calls.push([id, ids]); if (calls.length === 1) await first.promise }, submit: async () => result })
  session.change(91, [95]); session.change(91, [95, 96]); session.change(91, [96])
  await Promise.resolve()
  assert.deepEqual(calls, [[91, [95]]])
  assert.deepEqual(session.getSnapshot().answers[91], [96])
  first.resolve(); await session.flush()
  assert.deepEqual(calls, [[91, [95]], [91, [95, 96]], [91, [96]]])
  assert.deepEqual(session.getSnapshot().answers[91], [96])
})
await test('제출은 마지막 저장을 기다리며 중복 클릭은 한 번만 전송하고 입력을 잠금', async () => {
  const saving = deferred(), events = []
  const session = createAnswerSession(paper, { save: async () => { events.push('save'); await saving.promise; events.push('saved') }, submit: async () => { events.push('submit'); return result } })
  session.change(91, [95])
  const first = session.submit(), second = session.submit()
  session.change(91, [96])
  assert.equal(first, second)
  await Promise.resolve()
  assert.deepEqual(events, ['save'])
  assert.deepEqual(session.getSnapshot().answers[91], [95])
  saving.resolve(); assert.equal(await first, result)
  assert.deepEqual(events, ['save', 'saved', 'submit'])
  session.change(91, [96])
  assert.deepEqual(session.getSnapshot().answers[91], [95])
})
await test('저장 실패를 표시하고 제출을 차단하며 최신 답안 재저장 후 제출 가능', async () => {
  let fail = true, submissions = 0
  const session = createAnswerSession(paper, { save: async () => { if (fail) throw new Error('private details') }, submit: async () => { submissions++; return result } })
  session.change(91, [95])
  await assert.rejects(session.flush())
  assert.match(session.getSnapshot().error, /저장하지 못했습니다/)
  assert.doesNotMatch(session.getSnapshot().error, /private/)
  await assert.rejects(session.submit())
  assert.equal(submissions, 0)
  fail = false; session.retry(); await session.flush(); await session.submit()
  assert.equal(submissions, 1)
})
await test('저장 409는 후속 저장과 제출을 막아 최신 상태 복구를 요구', async () => {
  let calls = 0
  const session = createAnswerSession(paper, { save: async () => { calls++; throw conflict() }, submit: async () => { throw new Error('must not submit') } })
  session.change(91, [95]); session.change(91, [96])
  await assert.rejects(session.flush())
  assert.equal(calls, 1)
  assert.equal(session.getSnapshot().conflict, true)
  assert.doesNotMatch(session.getSnapshot().error, /private trace/)
  await assert.rejects(session.submit())
})
await test('점수 소수점과 서버 remainingAttempts를 그대로 사용', () => {
  assert.equal(scoreLabel(92.5), '92.50점')
  assert.equal(scoreLabel(null), '미제출')
  assert.equal(examAction('IN_PROGRESS', [attempt], 99).remaining, 1)
  assert.match(examErrorMessage(conflict()), /이미 제출/)
})

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
try {
  const { ExamOverview } = await server.ssrLoadModule('/src/pages/employee/EmployeeExamSection.tsx')
  const { ExamQuestion } = await server.ssrLoadModule('/src/pages/employee/EmployeeExamPage.tsx')
  const { ExamResultView } = await server.ssrLoadModule('/src/pages/employee/EmployeeExamResultPage.tsx')
  const { employeeExamApi: viteExamApi, loadEmployeeAttempt } = await server.ssrLoadModule('/src/api/employeeExamApi.ts')
  const { employeeLearningApi: viteLearningApi } = await server.ssrLoadModule('/src/api/employeeLearningApi.ts')
  const render = element => renderToStaticMarkup(h(MemoryRouter, null, element))
  await test('시험 없음과 각 시험 Action을 렌더링', () => {
    const view = (history, status = 'IN_PROGRESS', exam = detail.exam) => render(h(ExamOverview, { detail: { ...detail, status, exam }, history, pending: false, onStart() {} }))
    assert.match(view([], 'ASSIGNED', null), /시험이 없습니다/)
    assert.match(view([]), /시험 응시하기/)
    assert.match(view([attempt]), /시험 계속하기/)
    assert.doesNotMatch(view([attempt]), /<span>0\.00점/)
    assert.match(view([{ ...result, passed: false }]), /다시 응시하기/)
    assert.match(view([result]), /시험 결과 보기/)
    assert.doesNotMatch(view([result]), /다시 응시하기|시험 응시하기/)
    for (const status of ['FAILED', 'EXPIRED', 'COMPLETED']) assert.doesNotMatch(view([attempt], status), /시험 계속하기|시험 응시하기|다시 응시하기/)
  })
  await test('세 유형은 semantic input, 실제 Choice text/순서, 선택 상태를 렌더링', () => {
    for (const question of paper.questions) {
      const html = render(h(ExamQuestion, { question, selected: question.selectedChoiceIds, disabled: false, onChange() {} }))
      assert.match(html, /<fieldset/); assert.match(html, /<legend/)
      assert.ok(html.includes(`type="${question.questionType === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'}"`))
      assert.doesNotMatch(html, /isCorrect|correctChoiceIds|정답을 \d개|획득 점수/)
    }
    const html = render(h(ExamQuestion, { question: paper.questions[0], selected: [83], disabled: true, onChange() {} }))
    assert.ok(html.indexOf('선택 가') < html.indexOf('선택 나'))
    assert.match(html, /disabled=""/); assert.match(html, /checked=""/)
  })
  await test('제출 확인은 미응답 수와 수정 불가를 안내', () => {
    assert.match(submissionDescription(5, 4), /미응답 1문항/)
    assert.match(submissionDescription(5, 0), /미응답 5문항/)
    assert.match(submissionDescription(5, 5), /수정할 수 없습니다/)
  })
  await test('결과는 서버 passed와 Enrollment 상태를 별개로 표시', () => {
    const view = (status, value = result) => render(h(ExamResultView, { detail: { ...detail, status }, result: value }))
    assert.match(view('IN_PROGRESS'), /92.50점/)
    assert.match(view('IN_PROGRESS'), /남은 필수 학습/)
    assert.doesNotMatch(view('IN_PROGRESS'), /교육과정을 수료했습니다/)
    assert.match(view('COMPLETED'), /교육과정을 수료했습니다/)
    assert.match(view('FAILED', { ...result, passed: false, score: 42.75, remainingAttempts: 0 }), /불합격/)
    assert.match(view('FAILED'), /실패 상태로 종료/)
    assert.match(view('EXPIRED'), /기간이 만료/)
  })
  await test('새로고침은 history의 실제 Attempt와 paper 저장 답안을 복원', async () => {
    const calls = []
    viteLearningApi.detail = async () => detail
    viteExamApi.history = async () => [attempt]
    viteExamApi.paper = async id => { calls.push(id); return paper }
    const loaded = await loadEmployeeAttempt(10, 71)
    assert.equal(loaded.attempt.attemptNumber, 2)
    assert.deepEqual(loaded.paper.questions[0].selectedChoiceIds, [83])
    assert.deepEqual(calls, [71])
  })
  await test('수강 이력에 없는 Attempt는 paper API를 호출하지 않음', async () => {
    viteExamApi.history = async () => [attempt]
    viteExamApi.paper = async () => { assert.fail('unrelated attempt must not be requested') }
    assert.equal((await loadEmployeeAttempt(10, 999)).attempt, null)
  })
  await test('제출된 Attempt와 종료 수강은 편집용 paper를 호출하지 않음', async () => {
    viteExamApi.history = async () => [result]
    assert.equal((await loadEmployeeAttempt(10, 71)).paper, null)
    viteExamApi.history = async () => [attempt]
    viteLearningApi.detail = async () => ({ ...detail, status: 'EXPIRED' })
    assert.equal((await loadEmployeeAttempt(10, 71)).paper, null)
  })
} finally { await server.close() }
