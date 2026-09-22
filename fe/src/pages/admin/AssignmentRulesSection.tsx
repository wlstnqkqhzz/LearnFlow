import { useCallback, useState } from 'react'
import type { CourseStatus } from '../../api/courseTypes.ts'
import type { AssignmentRule } from '../../api/assignmentTypes.ts'
import { assignmentRuleApi } from '../../api/assignmentRuleApi.ts'
import { ActiveBadge, EmptyState, ErrorState, Feedback, LoadingState } from '../../components/admin/AdminUI.tsx'
import { ConfirmDialog } from '../../components/admin/Modal.tsx'
import { useAction } from '../../hooks/useAction.ts'
import { useRemote } from '../../hooks/useRemote.ts'
import { loadOrganizationOptions } from './organizationOptions.ts'
import { AssignmentRuleModal } from './AssignmentRuleModal.tsx'
import { ruleTarget, ruleTypes } from './assignmentUtils.ts'

export function AssignmentRulesSection({ courseId, courseStatus }: { courseId: number; courseStatus: CourseStatus }) {
  const loader = useCallback((signal: AbortSignal) => assignmentRuleApi.list(courseId, signal), [courseId])
  const query = useRemote(loader); const organizations = useRemote(loadOrganizationOptions); const action = useAction()
  const [editing, setEditing] = useState<'create' | AssignmentRule | null>(null); const [changing, setChanging] = useState<AssignmentRule | null>(null)
  const guide = courseStatus === 'DRAFT' ? '과정을 오픈하면 활성 배정 규칙이 적용됩니다.' : courseStatus === 'OPEN' ? '활성 규칙을 생성하거나 변경하면 새 대상자에게 자동 배정될 수 있습니다.' : '종료된 과정에는 새로운 자동 배정이 생성되지 않습니다.'
  async function changeStatus() { if (!changing) return; const changed = await action.run(async () => { await assignmentRuleApi.status(courseId, changing.id, !changing.active) }, changing.active ? '배정 규칙을 비활성화했습니다.' : '배정 규칙을 활성화했습니다.'); if (changed) { setChanging(null); query.reload() } }
  return <section className="surface detail-section course-content-section"><div className="section-heading"><div><h2>배정 규칙</h2><p className="admin-hint">{guide} 규칙 변경 후에도 기존 배정 이력은 유지됩니다.</p></div><button className="admin-button primary-button" disabled={!organizations.data} onClick={() => setEditing('create')}>+ 배정 규칙 추가</button></div><Feedback error={!changing ? action.error : ''} success={action.success} />
    {organizations.error && <ErrorState message={organizations.error} retry={organizations.reload} />}
    {query.loading ? <LoadingState /> : query.error ? <ErrorState message={query.error} retry={query.reload} /> : !query.data?.length ? <EmptyState message="등록된 배정 규칙이 없습니다." /> : <div className="table-scroll" tabIndex={0} role="region" aria-label="배정 규칙 목록"><table className="admin-table rule-table"><thead><tr><th>배정 유형</th><th>대상</th><th>상태</th><th>관리</th></tr></thead><tbody>{query.data.map(rule => <tr key={rule.id}><th>{ruleTypes[rule.ruleType]}<small className="table-secondary">규칙 #{rule.id}</small></th><td>{ruleTarget(rule, organizations.data?.departments ?? [], organizations.data?.jobs ?? [])}</td><td><ActiveBadge active={rule.active} /></td><td><span className="row-actions"><button className="admin-button" disabled={!organizations.data || action.pending} onClick={() => setEditing(rule)}>수정</button><button className={`admin-button ${rule.active ? 'danger-button' : ''}`} disabled={action.pending} onClick={() => { action.clear(); setChanging(rule) }}>{rule.active ? '비활성화' : '활성화'}</button></span></td></tr>)}</tbody></table></div>}
    {editing && organizations.data && <AssignmentRuleModal courseId={courseId} courseStatus={courseStatus} rule={editing === 'create' ? undefined : editing} departments={organizations.data.departments} jobs={organizations.data.jobs} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); query.reload() }} />}
    {changing && <ConfirmDialog title={`배정 규칙 ${changing.active ? '비활성화' : '활성화'}`} description={changing.active ? '기존 수강 기록은 유지되며 이 규칙을 통한 새로운 자동 배정만 중단됩니다.' : courseStatus === 'OPEN' ? '현재 조건에 해당하면서 아직 배정되지 않은 직원에게 교육이 자동 배정될 수 있습니다.' : '규칙을 활성화합니다. 과정이 오픈되면 자동 배정에 사용됩니다.'} label={changing.active ? '비활성화' : '활성화'} tone={changing.active ? 'danger' : 'primary'} pending={action.pending} error={action.error} onClose={() => setChanging(null)} onConfirm={() => void changeStatus()} />}
  </section>
}
