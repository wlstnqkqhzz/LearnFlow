// 직원 전용 DTO. 관리자 문항/선택지 타입과 분리한다.
export type EmployeeExamSummary = { examId: number; title: string; passingScore: number; maxAttempts: number }
export type EmployeeQuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE'
export type EmployeeExamChoice = { choiceId: number; choiceText: string; sortOrder: number }
export type EmployeeExamQuestion = {
  questionId: number
  questionType: EmployeeQuestionType
  questionText: string
  score: number
  sortOrder: number
  choices: EmployeeExamChoice[]
  selectedChoiceIds: number[]
}
export type EmployeeExamPaper = { attemptId: number; title: string; questions: EmployeeExamQuestion[] }
export type EmployeeExamAnswer = { questionId: number; selectedChoiceIds: number[] }
export type EmployeeExamAttempt = {
  attemptId: number
  attemptNumber: number
  score: number | null
  passed: boolean | null
  startedAt: string
  submittedAt: string | null
  maxAttempts: number
  remainingAttempts: number
}
