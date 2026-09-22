import type { AdminQuestion, QuestionType } from '../../api/examTypes.ts'

export const questionTypes: Record<QuestionType, string> = {
  SINGLE_CHOICE: '단일 선택',
  MULTIPLE_CHOICE: '복수 선택',
  TRUE_FALSE: 'O / X',
}

export function moveQuestion(ids: number[], index: number, direction: -1 | 1) {
  const target = index + direction
  if (target < 0 || target >= ids.length) return ids
  const next = [...ids]
  ;[next[index], next[target]] = [next[target], next[index]]
  return next
}

export function totalQuestionScore(questions: AdminQuestion[]) {
  return questions.reduce((total, question) => total + Number(question.score), 0)
}

export function validateAnswers(type: QuestionType, choices: { text: string; correct: boolean }[]) {
  if (choices.some(choice => !choice.text.trim())) return '모든 선택지 내용을 입력해 주세요.'
  const correct = choices.filter(choice => choice.correct).length
  if (type === 'SINGLE_CHOICE' && correct !== 1) return '단일 선택 문항은 정답을 정확히 1개 선택해야 합니다.'
  if (type === 'MULTIPLE_CHOICE' && correct < 1) return '복수 선택 문항은 정답을 1개 이상 선택해야 합니다.'
  if (type === 'TRUE_FALSE' && (choices.length !== 2 || correct !== 1)) return 'O / X 문항은 정답을 정확히 1개 선택해야 합니다.'
  return ''
}
