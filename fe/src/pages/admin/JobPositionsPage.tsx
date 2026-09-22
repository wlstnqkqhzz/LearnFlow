import { useCallback, useState, type FormEvent } from 'react'
import { jobPositionApi } from '../../api/jobPositionApi'
import type { JobPosition } from '../../api/adminTypes'
import { useRemote } from '../../hooks/useRemote'
import { useAction } from '../../hooks/useAction'
import { ActiveBadge, EmptyState, ErrorState, Feedback, LoadingState, PageHeader, SubmitButton } from '../../components/admin/AdminUI'
import { ConfirmDialog, Modal } from '../../components/admin/Modal'
import { OrganizationFields } from '../../components/admin/OrganizationFields'

export function JobPositionsPage() {
  const [active, setActive] = useState(false)
  const loader = useCallback((signal: AbortSignal) => jobPositionApi.list(active, signal), [active])
  const query = useRemote(loader)
  const action = useAction()
  const [editing, setEditing] = useState<JobPosition | null | undefined>(undefined)
  const [confirm, setConfirm] = useState<JobPosition | null>(null)
  function open(item: JobPosition | null) { action.clear(); setEditing(item) }
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const name = String(data.get('name')).trim()
    await action.run(async () => {
      if (editing) await jobPositionApi.update(editing.id, { name })
      else await jobPositionApi.create({ code: String(data.get('code')).trim(), name })
      setEditing(undefined); query.reload()
    }, editing ? '직무를 수정했습니다.' : '직무를 등록했습니다.')
  }
  async function changeStatus(item: JobPosition) {
    await action.run(async () => { await jobPositionApi.status(item.id, !item.isActive); setConfirm(null); query.reload() }, item.isActive ? '직무를 비활성화했습니다.' : '직무를 활성화했습니다.')
  }
  return <div className="management-page">
    <PageHeader title="직무 관리" description="직무 코드와 활성 상태를 관리합니다."><button className="admin-button primary-button" type="button" disabled={action.pending} onClick={() => open(null)}>+ 직무 추가</button></PageHeader>
    <div className="admin-toolbar"><label className="admin-field">표시 범위<select value={String(active)} onChange={event => setActive(event.target.value === 'true')}><option value="false">전체 직무</option><option value="true">활성 직무</option></select></label></div>
    <Feedback error={editing === undefined && !confirm ? action.error : ''} success={action.success} />
    <div className="surface admin-table-surface">{query.loading ? <LoadingState /> : query.error ? <ErrorState message={query.error} retry={query.reload} /> : !query.data?.length ? <EmptyState message={active ? '활성 직무가 없습니다.' : '등록된 직무가 없습니다. 첫 직무를 추가해 보세요.'}><button className="admin-button" onClick={() => open(null)}>직무 추가</button></EmptyState> : <div className="table-scroll" tabIndex={0} role="region" aria-label="직무 목록"><table className="admin-table"><thead><tr><th scope="col">직무명</th><th scope="col">코드</th><th scope="col">상태</th><th scope="col">관리</th></tr></thead><tbody>{query.data.map(item => <tr key={item.id}><th scope="row">{item.name}</th><td>{item.code}</td><td><ActiveBadge active={item.isActive} /></td><td><div className="row-actions"><button className="admin-button" disabled={action.pending} onClick={() => open(item)}>수정</button><button className="admin-button" disabled={action.pending} onClick={() => { action.clear(); if (item.isActive) setConfirm(item); else void changeStatus(item) }}>{item.isActive ? '비활성화' : '활성화'}</button></div></td></tr>)}</tbody></table></div>}</div>
    {editing !== undefined && <Modal title={editing ? '직무 수정' : '직무 추가'} busy={action.pending} onClose={() => setEditing(undefined)}><form className="admin-form" onSubmit={save}><fieldset disabled={action.pending}><OrganizationFields code={editing?.code} name={editing?.name} /></fieldset><Feedback error={action.error} /><div className="admin-actions"><SubmitButton pending={action.pending} label={editing ? '수정' : '등록'} /></div></form></Modal>}
    {confirm && <ConfirmDialog title={`${confirm.name} 비활성화`} description="이 직무를 비활성화하시겠습니까? 기존 회원의 직무 정보는 유지됩니다." label="비활성화" pending={action.pending} error={action.error} onClose={() => setConfirm(null)} onConfirm={() => void changeStatus(confirm)} />}
  </div>
}
