import { useRef, useState, type FormEvent } from 'react'
import { adminErrorCode, adminErrorMessage } from '../../api/adminError.ts'
import { questionApi } from '../../api/examApi.ts'
import type { AdminQuestion, QuestionType } from '../../api/examTypes.ts'
import { Feedback, SubmitButton } from '../../components/admin/AdminUI.tsx'
import { Modal } from '../../components/admin/Modal.tsx'
import { questionTypes, validateAnswers } from './examUtils.ts'
import { createAdditionalChoices } from './createAdditionalChoices.ts'

type ChoiceDraft = { id?: number; text: string; correct: boolean; sortOrder: number }
const defaults = (type: QuestionType): ChoiceDraft[] => type === 'TRUE_FALSE'
  ? [{ text: 'TRUE', correct: true, sortOrder: 1 }, { text: 'FALSE', correct: false, sortOrder: 2 }]
  : [{ text: '', correct: true, sortOrder: 1 }, { text: '', correct: false, sortOrder: 2 }]

export function QuestionModal({ courseId, question: initialQuestion, nextOrder, onClose, onSaved, onLocked }: {
  courseId: number
  question?: AdminQuestion
  nextOrder: number
  onClose: () => void
  onSaved: () => void
  onLocked: () => void
}) {
  const [question, setQuestion] = useState(initialQuestion)
  const [revision, setRevision] = useState(0)
  const [needsReload, setNeedsReload] = useState(false)
  const [type, setType] = useState<QuestionType>(question?.questionType ?? 'SINGLE_CHOICE')
  const [choices, setChoices] = useState<ChoiceDraft[]>(question?.choices.map(choice => ({ id: choice.choiceId, text: choice.choiceText, correct: choice.correct, sortOrder: choice.sortOrder })) ?? defaults('SINGLE_CHOICE'))
  const [pending, setPending] = useState(false)
  const busy = useRef(false)
  const [error, setError] = useState('')

  function changeType(next: QuestionType) {
    setType(next)
    if (!question) { setChoices(defaults(next)); return }
    if (next === 'TRUE_FALSE') {
      const first = choices[0]
      const second = choices[1]
      setChoices([
        { id: first?.id, text: 'TRUE', correct: true, sortOrder: 1 },
        { id: second?.id, text: 'FALSE', correct: false, sortOrder: 2 },
      ])
    } else if (next === 'SINGLE_CHOICE') {
      const selected = Math.max(0, choices.findIndex(choice => choice.correct))
      setChoices(current => current.map((choice, index) => ({ ...choice, correct: index === selected })))
    }
  }
  function setCorrect(index: number, checked: boolean) {
    setChoices(current => current.map((choice, choiceIndex) => ({ ...choice, correct: type === 'MULTIPLE_CHOICE' ? (choiceIndex === index ? checked : choice.correct) : choiceIndex === index })))
  }
  function removeChoice(index: number) { setChoices(current => current.filter((_, choiceIndex) => choiceIndex !== index).map((choice, choiceIndex) => ({ ...choice, sortOrder: choiceIndex + 1 }))) }
  function addChoice() { setChoices(current => [...current, { text: '', correct: false, sortOrder: current.length + 1 }]) }

  // 여러 선택지 요청 중 일부만 성공했으면 서버 ID와 순서를 복구한 뒤 다시 편집한다.
  async function reloadQuestion() {
    if (!question) return
    const current = (await questionApi.list(courseId)).find(item => item.questionId === question.questionId)
    if (!current) throw new Error('Question no longer exists')
    setQuestion(current)
    setType(current.questionType)
    setChoices(current.choices.map(choice => ({ id: choice.choiceId, text: choice.choiceText, correct: choice.correct, sortOrder: choice.sortOrder })))
    setRevision(value => value + 1)
    setNeedsReload(false)
  }
  async function retryReload() {
    if (busy.current) return
    busy.current = true; setPending(true)
    try { await reloadQuestion(); setError('최신 문항을 불러왔습니다. 내용을 확인한 뒤 다시 수정해 주세요.') }
    catch { setError('최신 문항을 불러오지 못했습니다. 연결을 확인한 뒤 다시 조회해 주세요.') }
    finally { busy.current = false; setPending(false) }
  }

  async function updateExisting(questionText: string, score: number) {
    if (!question) return
    const retained = choices.filter(choice => choice.id)
    const removed = question.choices.filter(choice => !retained.some(item => item.id === choice.choiceId))
    // TRUE_FALSE에서 다른 유형으로 바꾸는 경우 선택지 수를 늘리기 전에 유형부터 변경한다.
    if (question.questionType === 'TRUE_FALSE' && type !== 'TRUE_FALSE') {
      await questionApi.update(courseId, question.questionId, {
        questionType: type,
        correctChoiceIds: question.choices.filter(choice => choice.correct).map(choice => choice.choiceId),
      })
    }
    for (const choice of retained) {
      const before = question.choices.find(item => item.choiceId === choice.id)!
      if (before.choiceText !== choice.text.trim()) await questionApi.updateChoice(courseId, question.questionId, choice.id!, { choiceText: choice.text.trim() })
    }
    const created = await createAdditionalChoices(courseId, question.questionId, question.choices, choices.filter(choice => !choice.id))
    const ids = choices.map(choice => choice.id ?? created.shift()!.choiceId)
    const correctChoiceIds = ids.filter((_, index) => choices[index].correct)
    if (type === 'TRUE_FALSE' && question.questionType !== 'TRUE_FALSE') {
      // 기존 유형에서도 유효한 정답을 먼저 확정한 뒤 초과 선택지를 제거한다.
      await questionApi.update(courseId, question.questionId, { correctChoiceIds })
      for (const choice of removed) await questionApi.deleteChoice(courseId, question.questionId, choice.choiceId)
      await questionApi.update(courseId, question.questionId, { questionText, questionType: type, score, correctChoiceIds })
    } else {
      await questionApi.update(courseId, question.questionId, { questionText, questionType: type, score, correctChoiceIds })
      for (const choice of removed) await questionApi.deleteChoice(courseId, question.questionId, choice.choiceId)
    }
    await questionApi.reorderChoices(courseId, question.questionId, ids)
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy.current || needsReload) return
    const form = new FormData(event.currentTarget)
    const questionText = String(form.get('questionText') ?? '').trim()
    const score = Number(form.get('score'))
    const validation = validateAnswers(type, choices)
    if (validation) { setError(validation); return }
    busy.current = true; setPending(true); setError('')
    try {
      if (question) await updateExisting(questionText, score)
      else await questionApi.create(courseId, { questionText, questionType: type, score, sortOrder: nextOrder, choices: choices.map((choice, index) => ({ choiceText: choice.text.trim(), correct: choice.correct, sortOrder: index + 1 })) })
      onSaved()
    } catch (cause) {
      if (adminErrorCode(cause) === 'EXAM_CONFIGURATION_LOCKED') { onLocked(); onClose(); return }
      setError(adminErrorMessage(cause))
      if (question) {
        setNeedsReload(true)
        try {
          await reloadQuestion()
          setError(`${adminErrorMessage(cause)} 일부 변경이 저장되었을 수 있어 최신 문항을 불러왔습니다. 내용을 확인해 주세요.`)
        } catch { setError('저장 상태를 확인하지 못했습니다. 중복 저장을 방지하려면 최신 문항을 다시 조회해 주세요.') }
      }
    } finally { busy.current = false; setPending(false) }
  }

  return <Modal title={question ? '문항 수정' : '문항 추가'} onClose={onClose} busy={pending}>
    <form className="admin-form question-form" onSubmit={event => void submit(event)}>
      <Feedback error={error} />
      {needsReload && <button type="button" className="admin-button" disabled={pending} onClick={() => void retryReload()}>최신 문항 다시 조회</button>}
      <fieldset key={revision} disabled={pending || needsReload}>
        <label className="admin-field">문항 내용<textarea name="questionText" required rows={3} defaultValue={question?.questionText ?? ''} /></label>
        <div className="form-grid">
          <label className="admin-field">문항 유형<select value={type} onChange={event => changeType(event.target.value as QuestionType)}>{Object.entries(questionTypes).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>{question && <small>O / X로 변경하면 선택지가 TRUE / FALSE 두 개로 정리됩니다.</small>}</label>
          <label className="admin-field">배점<input name="score" type="number" required min="0.01" step="0.01" defaultValue={question?.score ?? 10} /></label>
        </div>
        <div className="choice-editor"><div className="section-heading"><div><strong>선택지 및 정답</strong><p className="admin-hint">{type === 'MULTIPLE_CHOICE' ? '정답을 하나 이상 선택합니다.' : '정답을 하나 선택합니다.'}</p></div>{type !== 'TRUE_FALSE' && <button type="button" className="admin-button" onClick={addChoice}>+ 선택지</button>}</div>
          {choices.map((choice, index) => <div className="choice-row" key={choice.id ?? `new-${index}`}>
            <input aria-label={`${index + 1}번 선택지를 정답으로 설정`} type={type === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'} name="correctChoice" checked={choice.correct} onChange={event => setCorrect(index, event.target.checked)} />
            <label className="admin-field"><span className="sr-only">{index + 1}번 선택지</span><input value={choice.text} required maxLength={1000} disabled={type === 'TRUE_FALSE'} onChange={event => setChoices(current => current.map((item, itemIndex) => itemIndex === index ? { ...item, text: event.target.value } : item))} /></label>
            {type !== 'TRUE_FALSE' && <button type="button" className="admin-button danger-button" aria-label={`${index + 1}번 선택지 삭제`} disabled={choices.length <= 1} onClick={() => removeChoice(index)}>삭제</button>}
          </div>)}
        </div>
      </fieldset>
      <div className="admin-actions"><button type="button" className="admin-button" disabled={pending} onClick={onClose}>취소</button>{!needsReload && <SubmitButton pending={pending} label={question ? '문항 저장' : '문항 추가'} />}</div>
    </form>
  </Modal>
}
