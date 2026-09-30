import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { retrainingApi } from '../../api/retrainingApi.ts'
import { PageHeader } from '../../components/admin/AdminUI.tsx'
import { useRemote } from '../../hooks/useRemote.ts'
import { RetrainingPolicyForm } from './RetrainingPolicyForm.tsx'
import { AutomationHelp, PolicyTable, RetrainingPagination, RetrainingState } from './RetrainingUI.tsx'

export function RetrainingPoliciesPage() {
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const navigate = useNavigate()
  const query = useRemote(useCallback((signal: AbortSignal) => retrainingApi.list(page, 20, signal), [page]))
  return <div className="management-page">
    <PageHeader title="재교육 관리" description="기존 교육과정을 기준으로 새 교육 회차를 생성하는 정책을 관리합니다.">
      <button type="button" className="admin-button primary-button" onClick={() => setCreating(true)}>+ 재교육 정책 생성</button>
    </PageHeader>
    <AutomationHelp />
    <div className="surface admin-table-surface">
      <RetrainingState loading={query.loading} error={query.error} empty={!query.data?.content.length} emptyMessage="등록된 재교육 정책이 없습니다." retry={query.reload}>
        {query.data && <PolicyTable policies={query.data.content} />}
      </RetrainingState>
      {query.data && <RetrainingPagination data={query.data} onPage={setPage} />}
    </div>
    {creating && <RetrainingPolicyForm onClose={() => setCreating(false)} onReload={() => { setCreating(false); query.reload() }} onSaved={policy => navigate(`/admin/retraining-policies/${policy.id}`)} />}
  </div>
}
