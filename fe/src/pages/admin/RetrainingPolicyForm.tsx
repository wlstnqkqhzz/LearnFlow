import { useCallback, useState, type FormEvent } from 'react'
import { adminErrorCode } from '../../api/adminError.ts'
import { courseApi } from '../../api/courseApi.ts'
import { retrainingApi } from '../../api/retrainingApi.ts'
import type { RetrainingPolicy, RetrainingPolicyRequest } from '../../api/retrainingTypes.ts'
import { Feedback, SubmitButton } from '../../components/admin/AdminUI.tsx'
import { Modal } from '../../components/admin/Modal.tsx'
import { useAction } from '../../hooks/useAction.ts'
import { useRemote } from '../../hooks/useRemote.ts'
import { AutomationHelp, RetrainingPagination, RetrainingState } from './RetrainingUI.tsx'
import { policyRequest, policyValidation, scheduleLocked } from './retrainingUtils.ts'

function SourceCoursePicker({ value, onChange }: { value: number; onChange: (id: number) => void }) {
  const [keyword, setKeyword] = useState('')
  const [search, setSearch] = useState({ page: 0, size: 20, keyword: '' })
  const query = useRemote(useCallback((signal: AbortSignal) => courseApi.list(search, signal), [search]))
  return <div className="admin-form">
    <div className="admin-toolbar"><label className="admin-field search-field">기준 과정 검색<input value={keyword} maxLength={200} onChange={event => setKeyword(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); setSearch({ ...search, page: 0, keyword: keyword.trim() }) } }} /></label>
      <button type="button" className="admin-button" onClick={() => setSearch({ ...search, page: 0, keyword: keyword.trim() })}>과정 검색</button></div>
    <label className="admin-field">기준 교육과정<select required value={value || ''} onChange={event => onChange(Number(event.target.value))}>
      <option value="">기준 교육과정 선택</option>
      {!!value && !query.data?.content.some(course => course.id === value) && <option value={value}>선택한 교육과정 #{value}</option>}
      {query.data?.content.map(course => <option key={course.id} value={course.id} disabled={!course.startDate}>{course.title} · #{course.id} · {course.status}{!course.startDate ? ' (시작일 설정 필요)' : ''}</option>)}
    </select><small>시작일이 있는 기존 과정을 선택합니다. 생성 시점의 구성과 배정 규칙을 복제합니다.</small></label>
    <RetrainingState loading={query.loading} error={query.error} empty={!query.data?.content.length} emptyMessage="검색 조건에 맞는 교육과정이 없습니다." retry={query.reload}>{null}</RetrainingState>
    {query.data && <RetrainingPagination data={query.data} onPage={page => setSearch({ ...search, page })} />}
  </div>
}

export function PolicyFields({ value, locked, onChange }: {
  value: RetrainingPolicyRequest; locked: boolean; onChange: (value: RetrainingPolicyRequest) => void
}) {
  const change = (patch: Partial<RetrainingPolicyRequest>) => onChange({ ...value, ...patch })
  return <>
    {locked ? <label className="admin-field">기준 교육과정<input aria-label="기준 교육과정" value={`교육과정 #${value.sourceCourseId}`} disabled /></label>
      : <SourceCoursePicker value={value.sourceCourseId} onChange={sourceCourseId => change({ sourceCourseId })} />}
    {locked && <p className="exam-lock-notice">회차 생성 또는 skip 이후에는 기준 과정과 핵심 일정을 수정할 수 없습니다. 일정 변경이 필요하면 기존 정책을 중지하고 새 정책을 생성해 주세요.</p>}
    <label className="admin-field">기본 제목<input required maxLength={150} value={value.baseTitle} onChange={event => change({ baseTitle: event.target.value })} /><small>새 시작일의 연·월과 회차가 서버에서 추가됩니다.</small></label>
    <fieldset className="form-grid" disabled={locked}>
      <label className="admin-field">반복 주기 (개월)<input type="number" min={1} max={1200} step={1} required value={value.intervalMonths || ''} onChange={event => change({ intervalMonths: Number(event.target.value) })} /></label>
      <label className="admin-field">첫 시작일<input type="date" min="1000-01-01" max="9999-12-31" required value={value.firstStartDate} onChange={event => change({ firstStartDate: event.target.value })} /></label>
      <label className="admin-field">운영 기간 (일)<input type="number" min={1} max={3660} step={1} required value={value.durationDays || ''} onChange={event => change({ durationDays: Number(event.target.value) })} /></label>
      <label className="admin-field">생성 선행 일수<input type="number" min={0} max={3660} step={1} required value={value.generationLeadDays} onChange={event => change({ generationLeadDays: Number(event.target.value) })} /></label>
    </fieldset>
    <label className="check-field"><input type="checkbox" checked={value.enabled} onChange={event => change({ enabled: event.target.checked })} />정책 활성화</label>
    <label className="check-field"><input type="checkbox" checked={value.autoCreate} onChange={event => change({ autoCreate: event.target.checked })} />자동 생성</label>
    <label className="check-field"><input type="checkbox" checked={value.autoOpen} onChange={event => change({ autoOpen: event.target.checked })} />자동 OPEN</label>
    <AutomationHelp />
    <p className="admin-hint">정책 중지는 기존에 생성된 교육과정을 삭제하거나 변경하지 않습니다.</p>
  </>
}

export function RetrainingPolicyForm({ policy, onClose, onSaved, onReload }: {
  policy?: RetrainingPolicy; onClose: () => void; onSaved: (policy: RetrainingPolicy) => void; onReload: () => void
}) {
  const [value, setValue] = useState<RetrainingPolicyRequest>(() => policy ? policyRequest(policy) : {
    sourceCourseId: 0, baseTitle: '', enabled: true, autoCreate: false, autoOpen: false,
    intervalMonths: 12, firstStartDate: '', durationDays: 30, generationLeadDays: 7,
  })
  const [validation, setValidation] = useState('')
  const [serverLocked, setServerLocked] = useState(false)
  const action = useAction()
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const request = policyRequest(value, policy)
    const invalid = policyValidation(request)
    setValidation(invalid)
    if (invalid || serverLocked) return
    let saved: RetrainingPolicy | undefined
    const success = await action.run(async () => {
      try { saved = policy ? await retrainingApi.update(policy.id, request) : await retrainingApi.create(request) }
      catch (error) { if (adminErrorCode(error) === 'RETRAINING_SCHEDULE_LOCKED') setServerLocked(true); throw error }
    }, '정책을 저장했습니다.')
    if (success && saved) onSaved(saved)
  }
  return <Modal title={policy ? '재교육 정책 수정' : '재교육 정책 생성'} busy={action.pending} onClose={onClose}>
    <form className="admin-form" onSubmit={event => void submit(event)}>
      <Feedback error={validation || action.error} />
      <fieldset disabled={action.pending}><PolicyFields value={value} onChange={setValue} locked={scheduleLocked(policy) || serverLocked} /></fieldset>
      <div className="admin-actions"><button type="button" className="admin-button" disabled={action.pending} onClick={onClose}>취소</button>
        {serverLocked ? <button type="button" className="admin-button" onClick={onReload}>최신 정책 다시 조회</button> : <SubmitButton pending={action.pending} label="정책 저장" />}</div>
    </form>
  </Modal>
}
