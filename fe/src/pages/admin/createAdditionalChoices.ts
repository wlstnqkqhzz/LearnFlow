import { questionApi } from '../../api/examApi.ts'
import type { AdminQuestionChoice } from '../../api/examTypes.ts'

// 재정렬 전까지 기존 선택지가 남아 있으므로 서버 순서와 겹치지 않게 추가한다.
export async function createAdditionalChoices(
  courseId: number,
  questionId: number,
  existing: AdminQuestionChoice[],
  drafts: { text: string }[],
) {
  const created: AdminQuestionChoice[] = []
  const lastOrder = existing.reduce((max, choice) => Math.max(max, choice.sortOrder), 0)
  for (const draft of drafts) {
    created.push(await questionApi.createChoice(courseId, questionId, {
      choiceText: draft.text.trim(), correct: false, sortOrder: lastOrder + created.length + 1,
    }))
  }
  return created
}
