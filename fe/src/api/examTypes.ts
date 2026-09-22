export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE'

export type Exam = {
  examId: number
  courseId: number
  title: string
  passingScore: number
  maxAttempts: number
  questionCount: number
  totalQuestionScore: number
}

export type ExamCreateRequest = {
  title: string
  passingScore: number
  maxAttempts: number
}

export type ExamPatchRequest = Partial<ExamCreateRequest>

// 정답을 포함하므로 관리자 화면에서만 사용한다.
export type AdminQuestionChoice = {
  choiceId: number
  choiceText: string
  correct: boolean
  sortOrder: number
}

export type AdminQuestion = {
  questionId: number
  questionType: QuestionType
  questionText: string
  score: number
  sortOrder: number
  choices: AdminQuestionChoice[]
}

export type ChoiceCreateRequest = {
  choiceText: string
  correct: boolean
  sortOrder: number
}

export type ChoicePatchRequest = Partial<ChoiceCreateRequest>

export type QuestionCreateRequest = {
  questionText: string
  questionType: QuestionType
  score: number
  sortOrder: number
  choices: ChoiceCreateRequest[]
}

export type QuestionPatchRequest = {
  questionText?: string
  questionType?: QuestionType
  score?: number
  sortOrder?: number
  correctChoiceIds?: number[]
}
