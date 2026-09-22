import { useCallback, useState, type FormEvent } from 'react'
import { departmentApi } from '../../api/departmentApi'
import type { Department } from '../../api/adminTypes'
import { useRemote } from '../../hooks/useRemote'
import { useAction } from '../../hooks/useAction'
import { ActiveBadge, EmptyState, ErrorState, Feedback, LoadingState, PageHeader, SubmitButton } from '../../components/admin/AdminUI'
import { ConfirmDialog, Modal } from '../../components/admin/Modal'
import { OrganizationFields } from '../../components/admin/OrganizationFields'
import { departmentRows, organizationName, parentCandidates } from './adminUtils'

export function DepartmentsPage() {
  const [active, setActive] = useState(false)
  const loader = useCallback(async (signal: AbortSignal) => {
    const all = await departmentApi.list(false, signal)
    return { all, items: active ? await departmentApi.list(true, signal) : all }
  }, [active])
  const query = useRemote(loader)
  const action = useAction()
  const [editing, setEditing] = useState<Department | null | undefined>(undefined)
  const [confirm, setConfirm] = useState<Department | null>(null)
  function open(item: Department | null) { action.clear(); setEditing(item) }
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const name = String(data.get('name')).trim()
    const parentDepartmentId = data.get('parentDepartmentId') ? Number(data.get('parentDepartmentId')) : null
    await action.run(async () => {
      if (editing) await departmentApi.update(editing.id, { name, ...(parentDepartmentId !== editing.parentDepartmentId ? { parentDepartmentId } : {}) })
      else await departmentApi.create({ code: String(data.get('code')).trim(), name, parentDepartmentId })
      setEditing(undefined); query.reload()
    }, editing ? '부서를 수정했습니다.' : '부서를 등록했습니다.')
  }
  async function changeStatus(item: Department) {
    await action.run(async () => { await departmentApi.status(item.id, !item.isActive); setConfirm(null); query.reload() }, item.isActive ? '부서를 비활성화했습니다.' : '부서를 활성화했습니다.')
  }
  const rows = departmentRows(query.data?.items ?? [])
  return <div className="management-page">
    <PageHeader title="조직 관리" description="부서의 계층과 활성 상태를 관리합니다."><button className="admin-button primary-button" type="button" disabled={!query.data || action.pending} onClick={() => open(null)}>+ 부서 추가</button></PageHeader>
    <div className="admin-toolbar"><label className="admin-field">표시 범위<select value={String(active)} onChange={event => setActive(event.target.value === 'true')}><option value="false">전체 부서</option><option value="true">활성 부서</option></select></label></div>
    <Feedback error={editing === undefined && !confirm ? action.error : ''} success={action.success} />
    <div className="surface admin-table-surface">
      {query.loading ? <LoadingState /> : query.error ? <ErrorState message={query.error} retry={query.reload} /> : !rows.length ? <EmptyState message={active ? '활성 부서가 없습니다.' : '등록된 부서가 없습니다. 첫 부서를 추가해 보세요.'}><button className="admin-button" onClick={() => open(null)}>부서 추가</button></EmptyState> :
        <div className="table-scroll" tabIndex={0} role="region" aria-label="부서 목록"><table className="admin-table"><thead><tr><th scope="col">부서명</th><th scope="col">코드</th><th scope="col">상위 부서</th><th scope="col">상태</th><th scope="col">관리</th></tr></thead><tbody>{rows.map(({ department: item, depth }) => <tr key={item.id}><th scope="row"><span style={{ paddingLeft: `${Math.min(depth, 8) * 16}px` }}>{depth > 0 && <span aria-hidden="true">└ </span>}{item.name}</span></th><td>{item.code}</td><td>{item.parentDepartmentId === null ? '없음' : organizationName(query.data?.all ?? [], item.parentDepartmentId)}</td><td><ActiveBadge active={item.isActive} /></td><td><div className="row-actions"><button className="admin-button" disabled={action.pending} onClick={() => open(item)}>수정</button><button className="admin-button" disabled={action.pending} onClick={() => { action.clear(); if (item.isActive) setConfirm(item); else void changeStatus(item) }}>{item.isActive ? '비활성화' : '활성화'}</button></div></td></tr>)}</tbody></table></div>}
    </div>
    {editing !== undefined && <Modal title={editing ? '부서 수정' : '부서 추가'} busy={action.pending} onClose={() => setEditing(undefined)}><form className="admin-form" onSubmit={save}><fieldset disabled={action.pending}><OrganizationFields code={editing?.code} name={editing?.name} /><label className="admin-field">상위 부서<select name="parentDepartmentId" defaultValue={editing?.parentDepartmentId ?? ''}><option value="">없음 (최상위 부서)</option>{parentCandidates(query.data?.all ?? [], editing ?? undefined).map(item => <option key={item.id} value={item.id}>{item.name}{!item.isActive ? ' (비활성·현재 상위 부서)' : ''}</option>)}</select></label></fieldset><Feedback error={action.error} /><div className="admin-actions"><SubmitButton pending={action.pending} label={editing ? '수정' : '등록'} /></div></form></Modal>}
    {confirm && <ConfirmDialog title={`${confirm.name} 비활성화`} description="이 부서를 비활성화하시겠습니까? 기존 하위 부서와 회원의 소속은 유지됩니다." label="비활성화" pending={action.pending} error={action.error} onClose={() => setConfirm(null)} onConfirm={() => void changeStatus(confirm)} />}
  </div>
}
