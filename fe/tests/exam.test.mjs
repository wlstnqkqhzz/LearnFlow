import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { createServer } from 'vite'
import { apiClient } from '../src/api/client.ts'
import { examApi, questionApi } from '../src/api/examApi.ts'
import { moveQuestion, questionTypes, totalQuestionScore, validateAnswers } from '../src/pages/admin/examUtils.ts'

function capture(data) {
  const calls = []
  apiClient.defaults.adapter = async config => { calls.push(config); return { config, data, status: 200, statusText: '', headers: {} } }
  return calls
}
function body(call) { return call.data ? JSON.parse(call.data) : undefined }

const exam = { examId: 4, courseId: 7, title: '정보보안 최종 평가', passingScore: 80, maxAttempts: 3, questionCount: 2, totalQuestionScore: 30 }
const questions = [
  { questionId: 11, questionType: 'SINGLE_CHOICE', questionText: '올바른 정책은?', score: 10, sortOrder: 1, choices: [{ choiceId: 101, choiceText: '정답', correct: true, sortOrder: 1 }, { choiceId: 102, choiceText: '오답', correct: false, sortOrder: 2 }] },
  { questionId: 12, questionType: 'TRUE_FALSE', questionText: '자료를 개인 메일로 전송해도 된다.', score: 20, sortOrder: 2, choices: [{ choiceId: 103, choiceText: 'TRUE', correct: false, sortOrder: 1 }, { choiceId: 104, choiceText: 'FALSE', correct: true, sortOrder: 2 }] },
]

await test('시험 조회·생성·수정·구성 검증은 Course 소속 API를 사용', async () => {
  const calls = capture(exam)
  await examApi.get(7)
  await examApi.create(7, { title: exam.title, passingScore: 80, maxAttempts: 3 })
  await examApi.update(7, { passingScore: 85 })
  await examApi.validate(7)
  assert.deepEqual(calls.map(call => [call.method, call.url]), [['get', '/courses/7/exam'], ['post', '/courses/7/exam'], ['patch', '/courses/7/exam'], ['get', '/courses/7/exam/validation']])
  assert.deepEqual(body(calls[1]), { title: exam.title, passingScore: 80, maxAttempts: 3 })
})

await test('문항 생성·수정·삭제와 전체 순서 저장 Payload가 실제 계약과 일치', async () => {
  const calls = capture(questions)
  await questionApi.list(7)
  await questionApi.create(7, { questionText: '문항', questionType: 'MULTIPLE_CHOICE', score: 20, sortOrder: 3, choices: [{ choiceText: 'A', correct: true, sortOrder: 1 }] })
  await questionApi.update(7, 11, { questionText: '수정', correctChoiceIds: [101] })
  await questionApi.reorder(7, [12, 11])
  await questionApi.delete(7, 11)
  assert.deepEqual(calls.map(call => [call.method, call.url]), [['get', '/courses/7/exam/questions'], ['post', '/courses/7/exam/questions'], ['patch', '/courses/7/exam/questions/11'], ['patch', '/courses/7/exam/questions/order'], ['delete', '/courses/7/exam/questions/11']])
  assert.deepEqual(body(calls[3]), { questionIds: [12, 11] })
})

await test('선택지 생성·수정·삭제·순서 저장은 문항 소속 경로 사용', async () => {
  const calls = capture(questions[0].choices[0])
  await questionApi.createChoice(7, 11, { choiceText: '새 선택지', correct: false, sortOrder: 3 })
  await questionApi.updateChoice(7, 11, 101, { choiceText: '수정 선택지' })
  await questionApi.reorderChoices(7, 11, [102, 101])
  await questionApi.deleteChoice(7, 11, 101)
  assert.deepEqual(calls.map(call => [call.method, call.url]), [['post', '/courses/7/exam/questions/11/choices'], ['patch', '/courses/7/exam/questions/11/choices/101'], ['patch', '/courses/7/exam/questions/11/choices/order'], ['delete', '/courses/7/exam/questions/11/choices/101']])
  assert.deepEqual(body(calls[2]), { choiceIds: [102, 101] })
})

await test('문항 유형 Label과 정답 규칙을 구분', () => {
  assert.deepEqual(questionTypes, { SINGLE_CHOICE: '단일 선택', MULTIPLE_CHOICE: '복수 선택', TRUE_FALSE: 'O / X' })
  assert.match(validateAnswers('SINGLE_CHOICE', [{ text: 'A', correct: false }]), /정확히 1개/)
  assert.equal(validateAnswers('SINGLE_CHOICE', [{ text: 'A', correct: true }, { text: 'B', correct: false }]), '')
  assert.match(validateAnswers('MULTIPLE_CHOICE', [{ text: 'A', correct: false }]), /1개 이상/)
  assert.equal(validateAnswers('MULTIPLE_CHOICE', [{ text: 'A', correct: true }, { text: 'B', correct: true }]), '')
  assert.equal(validateAnswers('TRUE_FALSE', [{ text: 'TRUE', correct: true }, { text: 'FALSE', correct: false }]), '')
})

await test('문항 이동과 총 배점 계산은 원본을 변경하지 않음', () => {
  const ids = [11, 12, 13]
  assert.deepEqual(moveQuestion(ids, 1, -1), [12, 11, 13])
  assert.deepEqual(ids, [11, 12, 13])
  assert.equal(totalQuestionScore(questions), 30)
})

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
try {
  const { QuestionList } = await server.ssrLoadModule('/src/pages/admin/ExamPage.tsx')
  const { ExamModal } = await server.ssrLoadModule('/src/pages/admin/ExamModal.tsx')
  const { QuestionModal } = await server.ssrLoadModule('/src/pages/admin/QuestionModal.tsx')
  const { CourseTable } = await server.ssrLoadModule('/src/pages/admin/CoursesPage.tsx')
  await test('관리자 문항 목록은 유형·배점·정답을 텍스트와 함께 표시', () => {
    const html = renderToStaticMarkup(h(QuestionList, { questions, orderedIds: [11, 12], locked: false, pending: false, onMove: () => {}, onEdit: () => {}, onDelete: () => {} }))
    assert.match(html, /단일 선택/)
    assert.match(html, /O \/ X/)
    assert.match(html, /10점/)
    assert.match(html, /정답/)
    assert.match(html, />X</)
  })
  await test('잠금 상태에서는 문항 변경 Action이 비활성화', () => {
    const html = renderToStaticMarkup(h(QuestionList, { questions, orderedIds: [11, 12], locked: true, pending: false, onMove: () => {}, onEdit: () => {}, onDelete: () => {} }))
    assert.ok((html.match(/disabled=""/g) ?? []).length >= 6)
  })
  await test('시험 Form은 0~100 합격 기준과 1회 이상 응시 횟수를 받는다', () => {
    const html = renderToStaticMarkup(h(ExamModal, { courseId: 7, onClose: () => {}, onSaved: () => {}, onLocked: () => {} }))
    assert.match(html, /name="passingScore"/)
    assert.match(html, /min="0"/)
    assert.match(html, /max="100"/)
    assert.match(html, /name="maxAttempts"/)
    assert.match(html, /min="1"/)
  })
  await test('문항 유형별로 radio·checkbox·고정 O\/X UI를 렌더링', () => {
    const base = { courseId: 7, nextOrder: 3, onClose: () => {}, onSaved: () => {}, onLocked: () => {} }
    const single = renderToStaticMarkup(h(QuestionModal, base))
    const multiple = renderToStaticMarkup(h(QuestionModal, { ...base, question: { ...questions[0], questionType: 'MULTIPLE_CHOICE' } }))
    const trueFalse = renderToStaticMarkup(h(QuestionModal, { ...base, question: questions[1] }))
    assert.match(single, /type="radio"/)
    assert.match(multiple, /type="checkbox"/)
    assert.match(trueFalse, /value="TRUE"/)
    assert.match(trueFalse, /value="FALSE"/)
    assert.doesNotMatch(trueFalse, />\+ 선택지</)
  })
  await test('전체 시험 관리 진입은 Course 목록을 재사용해 과정별 편집 경로로 이동', () => {
    const course = { id: 7, title: '보안교육', courseType: 'MANDATORY', status: 'DRAFT', startDate: null, endDate: null, passingProgressRate: 100, instructorName: null }
    const html = renderToStaticMarkup(h(MemoryRouter, null, h(CourseTable, { courses: [course], actionLabel: '시험 관리', actionPath: item => `/admin/courses/${item.id}/exam` })))
    assert.match(html, /\/admin\/courses\/7\/exam/)
    assert.match(html, /시험 관리/)
  })
} finally { await server.close() }
