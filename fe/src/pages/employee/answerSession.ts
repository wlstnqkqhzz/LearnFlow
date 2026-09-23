import axios from 'axios'
import type { EmployeeExamAttempt, EmployeeExamPaper } from '../../api/employeeExamTypes.ts'
import { examErrorMessage } from './employeeExamUtils.ts'

type Snapshot = {
  answers: Record<number, number[]>
  pending: number
  failed: number[]
  error: string
  conflict: boolean
  submitting: boolean
  submitted: boolean
}
type Transport = {
  save: (questionId: number, selected: number[]) => Promise<unknown>
  submit: () => Promise<EmployeeExamAttempt>
}

// 응시 하나의 저장 요청을 직렬화해 서버에도 마지막 선택이 마지막에 반영되도록 한다.
export function createAnswerSession(paper: EmployeeExamPaper, transport: Transport) {
  let snapshot: Snapshot = { answers: Object.fromEntries(paper.questions.map(q => [q.questionId, [...q.selectedChoiceIds]])),
    pending: 0, failed: [], error: '', conflict: false, submitting: false, submitted: false }
  const listeners = new Set<() => void>()
  const versions = new Map<number, number>()
  let tail: Promise<void> = Promise.resolve()
  let submission: Promise<EmployeeExamAttempt> | null = null
  function publish(patch: Partial<Snapshot>) {
    snapshot = { ...snapshot, ...patch }
    listeners.forEach(listener => listener())
  }
  function save(questionId: number, ids: number[]) {
    const version = (versions.get(questionId) ?? 0) + 1
    versions.set(questionId, version)
    const selected = [...ids]
    publish({ answers: { ...snapshot.answers, [questionId]: selected }, pending: snapshot.pending + 1 })
    tail = tail.then(async () => {
      try {
        if (snapshot.conflict) return
        await transport.save(questionId, selected)
        if (versions.get(questionId) === version) {
          const failed = snapshot.failed.filter(id => id !== questionId)
          publish({ failed, error: failed.length ? snapshot.error : '' })
        }
      } catch (cause) {
        const conflict = axios.isAxiosError(cause) && cause.response?.status === 409
        if (versions.get(questionId) === version || conflict) {
          publish({ failed: [...new Set([...snapshot.failed, questionId])],
            error: `답안을 저장하지 못했습니다. ${examErrorMessage(cause)}`, conflict: snapshot.conflict || conflict })
        }
      } finally { publish({ pending: snapshot.pending - 1 }) }
    })
  }
  async function flush() {
    await tail
    if (snapshot.failed.length || snapshot.conflict) throw new Error('답안 저장을 완료한 뒤 제출해 주세요.')
  }
  return {
    getSnapshot: () => snapshot,
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener) } },
    change(questionId: number, ids: number[]) {
      if (snapshot.submitting || snapshot.submitted || snapshot.conflict) return
      save(questionId, ids)
    },
    retry() {
      if (snapshot.submitting || snapshot.submitted || snapshot.conflict) return
      for (const id of snapshot.failed) save(id, snapshot.answers[id])
    },
    flush,
    submit() {
      if (submission) return submission
      publish({ submitting: true })
      submission = (async () => {
        try {
          await flush()
          const result = await transport.submit()
          publish({ submitted: true })
          return result
        } finally { publish({ submitting: false }) }
      })()
      void submission.catch(() => { submission = null })
      return submission
    },
  }
}
