import { useCallback, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { retrainingApi } from '../../api/retrainingApi.ts'
import type { Course } from '../../api/courseTypes.ts'
import { ErrorState, Feedback, PageHeader } from '../../components/admin/AdminUI.tsx'
import { ConfirmDialog } from '../../components/admin/Modal.tsx'
import { useAction } from '../../hooks/useAction.ts'
import { useRemote } from '../../hooks/useRemote.ts'
import { RetrainingPolicyForm } from './RetrainingPolicyForm.tsx'
import { AutomationHelp, OccurrenceTable, PolicySummary, RetrainingPagination, RetrainingState } from './RetrainingUI.tsx'
import { occurrenceValidation } from './retrainingUtils.ts'

type Operation = { type: 'status'; enabled: boolean } | { type: 'generate' | 'skip'; number: number }

export function OccurrenceActions({ pending, nextOccurrenceNumber, onRequest }: {
  pending: boolean; nextOccurrenceNumber: number; onRequest: (type: 'generate' | 'skip', number: number) => void
}) {
  const [number, setNumber] = useState('')
  const [validation, setValidation] = useState('')
  function request(type: 'generate' | 'skip') {
    const invalid = occurrenceValidation(number)
    setValidation(invalid)
    if (!invalid && !pending) onRequest(type, Number(number))
  }
  function submit(event: FormEvent) { event.preventDefault(); request('generate') }
  return <section className="surface detail-section admin-form"><h2>수동 회차 처리</h2>
    <p className="admin-hint">서버 기준 다음 회차는 {nextOccurrenceNumber}회차입니다. 처리할 회차 번호를 직접 입력해 주세요. 이미 생성된 회차를 다시 요청하면 기존 교육과정을 반환합니다.</p>
    <form className="admin-form" onSubmit={submit}>
      <fieldset disabled={pending}><label className="admin-field">처리할 회차 번호<input type="number" min={1} max={2147483647} step={1} required value={number} onChange={event => setNumber(event.target.value)} /></label>
        <div className="row-actions"><button type="submit" className="admin-button primary-button">지정 회차 생성 / 조회</button>
          <button type="button" className="admin-button danger-button" onClick={() => request('skip')}>지난 회차 건너뛰기</button></div></fieldset>
      <Feedback error={validation} />
    </form>
    <p className="admin-hint">수동 생성도 현재 서버 정책에 따라 검증됩니다. 자동 OPEN 사용 시 생성 직후 공개·배정됩니다.</p>
    <p className="admin-hint">건너뛰기는 종료일 또는 필수 과제 마감이 지난 미생성 회차에만 가능합니다. 서버가 가능 여부를 판정하며, 확인 후 지정한 회차 하나만 처리합니다.</p>
  </section>
}

export function RetrainingPolicyDetailPage() {
  const { policyId } = useParams()
  const id = Number(policyId)
  return Number.isSafeInteger(id) && id > 0 ? <PolicyDetail key={id} id={id} />
    : <div className="management-page"><ErrorState message="올바른 정책 ID가 아닙니다." retry={() => window.history.back()} /><Link to="/admin/retraining-policies">재교육 목록으로</Link></div>
}

function PolicyDetail({ id }: { id: number }) {
  const query = useRemote(useCallback((signal: AbortSignal) => retrainingApi.get(id, signal), [id]))
  const [page, setPage] = useState(0)
  const courses = useRemote(useCallback((signal: AbortSignal) => retrainingApi.courses(id, page, 20, signal), [id, page]))
  const [editing, setEditing] = useState(false)
  const [operation, setOperation] = useState<Operation | null>(null)
  const [resultCourse, setResultCourse] = useState<Course | null>(null)
  const action = useAction()
  const policy = query.data
  function reload() { query.reload(); courses.reload() }
  function ask(value: Operation) { action.clear(); setOperation(value) }
  async function confirm() {
    if (!operation) return
    const target = operation
    const success = await action.run(async () => {
      if (target.type === 'status') await retrainingApi.status(id, target.enabled)
      else if (target.type === 'skip') await retrainingApi.skip(id, target.number)
      else setResultCourse(await retrainingApi.generate(id, target.number))
    }, target.type === 'status' ? '정책 상태를 변경했습니다.' : target.type === 'skip' ? `${target.number}회차 건너뛰기를 처리했습니다.` : `${target.number}회차 교육과정을 확인했습니다.`)
    if (success) { setOperation(null); reload() }
  }
  return <div className="management-page">
    <PageHeader title="재교육 정책 상세" description="정책 설정과 생성된 교육 회차를 관리합니다."><div className="row-actions">
      <Link className="admin-button" to="/admin/retraining-policies">정책 목록</Link><button type="button" className="admin-button" disabled={action.pending} onClick={reload}>최신 정보 조회</button>
    </div></PageHeader>
    {!operation && <Feedback error={action.error} success={action.success} />}
    {resultCourse && <p className="admin-success retraining-result" role="status"><Link to={`/admin/courses/${resultCourse.id}`}>{resultCourse.occurrenceNumber}회차 · {resultCourse.title} 상세 보기</Link></p>}
    <RetrainingState loading={query.loading} error={query.error} empty={!policy} emptyMessage="재교육 정책이 없습니다." retry={query.reload}>
      {policy && <>
        <section className="surface detail-section admin-form"><PolicySummary policy={policy} /><AutomationHelp />
          <p className="admin-hint">중지는 기존 교육과정을 삭제하거나 변경하지 않습니다.</p>
          <div className="admin-actions"><button type="button" className="admin-button" disabled={action.pending} onClick={() => setEditing(true)}>정책 수정</button>
            <button type="button" className="admin-button" disabled={action.pending} onClick={() => ask({ type: 'status', enabled: !policy.enabled })}>{policy.enabled ? '정책 중지' : '정책 활성화'}</button></div>
        </section>
        <OccurrenceActions pending={action.pending} nextOccurrenceNumber={policy.nextOccurrenceNumber} onRequest={(type, number) => ask({ type, number })} />
      </>}
    </RetrainingState>
    <section className="surface admin-table-surface"><div className="enrollment-heading"><h2>생성된 회차</h2></div>
      <RetrainingState loading={courses.loading} error={courses.error} empty={!courses.data?.content.length} emptyMessage="생성된 회차가 없습니다." retry={courses.reload}>
        {courses.data && <OccurrenceTable courses={courses.data.content} />}
      </RetrainingState>
      {courses.data && <RetrainingPagination data={courses.data} disabled={action.pending} onPage={setPage} />}
    </section>
    {editing && policy && <RetrainingPolicyForm policy={policy} onClose={() => setEditing(false)} onSaved={() => { setEditing(false); reload() }} onReload={() => { setEditing(false); reload() }} />}
    {operation && <ConfirmDialog
      title={operation.type === 'status' ? (operation.enabled ? '정책 활성화' : '정책 중지') : operation.type === 'skip' ? '지난 회차 건너뛰기 확인' : '지정 회차 생성 확인'}
      description={operation.type === 'status'
        ? (operation.enabled ? '정책을 활성화할까요? 자동 생성이 켜져 있으면 예정일이 도래한 회차가 자동 생성 대상이 됩니다.' : '정책을 중지할까요? 이미 생성된 교육과정은 변경되지 않습니다.')
        : operation.type === 'skip'
          ? `${operation.number}회차를 건너뛰도록 요청할까요? 서버에서 지난 회차인지 확인합니다. 건너뛴 회차는 다시 생성할 수 없으며 기존 교육과정은 변경되지 않습니다.`
          : `${operation.number}회차를 생성하거나 기존 회차를 조회할까요? 자동 OPEN이 켜져 있으면 새 과정 생성 직후 공개 및 자동 배정이 실행됩니다.`}
      label={operation.type === 'skip' ? `${operation.number}회차 건너뛰기` : '확인'}
      tone={operation.type === 'skip' ? 'danger' : 'primary'} pending={action.pending} error={action.error}
      onClose={() => { setOperation(null); action.clear() }} onConfirm={() => void confirm()} />}
  </div>
}
