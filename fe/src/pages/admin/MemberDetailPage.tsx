import { useCallback, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { memberApi } from '../../api/memberApi'
import type { MemberStatus } from '../../api/adminTypes'
import type { Role } from '../../auth/authTypes'
import { useAuth } from '../../auth/AuthContext'
import { refreshAccessToken } from '../../api/client'
import { useRemote } from '../../hooks/useRemote'
import { useAction } from '../../hooks/useAction'
import { ErrorState, Feedback, LoadingState, MemberBadge, PageHeader, RoleBadges, SubmitButton } from '../../components/admin/AdminUI'
import { ConfirmDialog } from '../../components/admin/Modal'
import { OrganizationSelect } from '../../components/admin/OrganizationSelect'
import { allowedStatuses, organizationName, roleLabels } from './adminUtils'
import { loadOrganizationOptions } from './organizationOptions'

export function MemberDetailPage() {
  const { memberId } = useParams()
  const id = Number(memberId)
  if (!Number.isSafeInteger(id) || id <= 0) return <div className="management-page"><p role="alert">올바른 회원 주소가 아닙니다.</p><Link to="/admin/members">회원 목록으로</Link></div>
  return <MemberDetail key={id} id={id} />
}

function MemberDetail({ id }: { id: number }) {
  const loader = useCallback((signal: AbortSignal) => memberApi.get(id, signal), [id])
  const query = useRemote(loader)
  const options = useRemote(loadOrganizationOptions)
  const action = useAction()
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [confirm, setConfirm] = useState<'resign' | 'remove-admin' | null>(null)
  const self = user?.memberId === id
  const member = query.data

  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    await action.run(async () => {
      await memberApi.update(id, { name: String(form.get('name')).trim(), email: String(form.get('email')).trim(), hireDate: String(form.get('hireDate')) })
      if (self) await refreshAccessToken()
      query.reload()
    }, '기본 정보를 수정했습니다.')
  }
  async function saveOrganization(event: FormEvent<HTMLFormElement>, type: 'department' | 'jobPosition') {
    event.preventDefault()
    const value = Number(new FormData(event.currentTarget).get(`${type}Id`))
    await action.run(async () => {
      if (type === 'department') await memberApi.department(id, value)
      else await memberApi.jobPosition(id, value)
      query.reload()
    }, type === 'department' ? '부서를 변경했습니다.' : '직무를 변경했습니다.')
  }
  async function changeStatus(status: MemberStatus) {
    await action.run(async () => {
      await memberApi.status(id, status)
      setConfirm(null)
      if (self && status === 'RESIGNED') { await logout(); navigate('/login', { replace: true }); return }
      query.reload()
    }, status === 'RESIGNED' ? '퇴사 처리했습니다.' : status === 'ACTIVE' ? '복직 처리했습니다.' : '휴직 처리했습니다.')
  }
  async function changeRole(role: Exclude<Role, 'EMPLOYEE'>, enabled: boolean) {
    await action.run(async () => {
      if (enabled) await memberApi.addRole(id, role)
      else await memberApi.removeRole(id, role)
      setConfirm(null)
      if (self) {
        await refreshAccessToken()
        if (role === 'ADMIN' && !enabled) { navigate('/', { replace: true }); return }
      }
      query.reload()
    }, '역할을 변경했습니다.')
  }

  return <div className="management-page">
    <PageHeader title="회원 상세" description="기본 정보, 조직, 역할과 재직 상태를 각각 관리합니다."><Link className="admin-button" to="/admin/members">목록으로</Link></PageHeader>
    {location.state?.created && <p className="admin-success" role="status">회원이 등록되었습니다. 필요하면 추가 역할을 지정하세요.</p>}
    <Feedback error={!confirm ? action.error : ''} success={action.success} />
    {query.loading ? <LoadingState /> : query.error ? <ErrorState message={query.error} retry={query.reload} /> : member && <>
      <section className="member-summary surface"><div><h2>{member.name}</h2><p>{member.email} · 사번 {member.employeeNumber}</p></div><div className="row-actions"><RoleBadges roles={member.roles} /><MemberBadge status={member.status} /></div></section>
      {member.status === 'RESIGNED' && <p className="admin-hint">퇴사는 최종 상태입니다. 기본 정보·조직 정보 수정과 복직은 할 수 없습니다. 기존 교육 이력은 유지됩니다.</p>}
      <div className="member-detail-grid">
        <section className="detail-section surface"><h2>기본 정보</h2><form className="admin-form" onSubmit={saveProfile}><fieldset disabled={action.pending || member.status === 'RESIGNED'}><label className="admin-field">이름<input name="name" required maxLength={100} pattern=".*\S.*" defaultValue={member.name} /></label><label className="admin-field">이메일<input name="email" type="email" required maxLength={255} defaultValue={member.email} /></label><label className="admin-field">입사일<input name="hireDate" type="date" required defaultValue={member.hireDate} /></label></fieldset>{member.status !== 'RESIGNED' && <div className="admin-actions"><SubmitButton pending={action.pending} label="기본 정보 저장" /></div>}</form></section>
        <section className="detail-section surface"><h2>조직 정보</h2><p className="admin-hint">부서와 직무는 각각 저장됩니다.</p>{options.loading ? <LoadingState /> : options.error ? <ErrorState message={options.error} retry={options.reload} /> : options.data && <>
          <form className="admin-form" onSubmit={event => void saveOrganization(event, 'department')}><fieldset disabled={action.pending || member.status === 'RESIGNED'}><OrganizationSelect name="departmentId" label="현재 부서" items={options.data.departments} currentId={member.departmentId} /></fieldset><p className="admin-hint">{organizationName(options.data.departments, member.departmentId)}</p>{member.status !== 'RESIGNED' && <div className="admin-actions"><SubmitButton pending={action.pending} label="부서 변경" /></div>}</form>
          <form className="admin-form detail-divider" onSubmit={event => void saveOrganization(event, 'jobPosition')}><fieldset disabled={action.pending || member.status === 'RESIGNED'}><OrganizationSelect name="jobPositionId" label="현재 직무" items={options.data.jobs} currentId={member.jobPositionId} /></fieldset><p className="admin-hint">{organizationName(options.data.jobs, member.jobPositionId)}</p>{member.status !== 'RESIGNED' && <div className="admin-actions"><SubmitButton pending={action.pending} label="직무 변경" /></div>}</form>
        </>}</section>
        <section className="detail-section surface"><h2>역할 관리</h2><p className="admin-hint">복수 역할을 지정할 수 있습니다. 각 항목을 변경하면 즉시 저장됩니다.</p><fieldset className="role-options" disabled={action.pending}>{(['EMPLOYEE', 'INSTRUCTOR', 'ADMIN'] as Role[]).map(role => <label key={role}><input type="checkbox" checked={member.roles.includes(role)} disabled={role === 'EMPLOYEE' || action.pending} onChange={event => { if (role === 'EMPLOYEE') return; const enabled = event.target.checked; if (self && role === 'ADMIN' && !enabled) { action.clear(); setConfirm('remove-admin') } else void changeRole(role, enabled) }} />{roleLabels[role]}{role === 'EMPLOYEE' && <small>기본 역할·제거 불가</small>}</label>)}</fieldset>{action.pending && <p role="status" className="admin-hint">변경 사항을 처리하고 있습니다…</p>}</section>
        <section className="detail-section surface"><h2>재직 상태</h2><p className="status-summary"><MemberBadge status={member.status} /></p>{member.resignedAt && <p className="admin-hint">퇴사 시각 (UTC): {member.resignedAt.replace('T', ' ')}</p>}<div className="admin-actions">{allowedStatuses(member.status).map(status => <button key={status} className={`admin-button ${status === 'RESIGNED' ? 'danger-button' : ''}`} disabled={action.pending} onClick={() => { action.clear(); if (status === 'RESIGNED') setConfirm('resign'); else void changeStatus(status) }}>{action.pending ? '처리 중…' : status === 'RESIGNED' ? '퇴사 처리' : status === 'ACTIVE' ? '복직 처리' : '휴직 처리'}</button>)}</div></section>
      </div>
      {confirm && <ConfirmDialog title={confirm === 'resign' ? `${member.name} 회원 퇴사 처리` : '내 관리자 역할 제거'} description={confirm === 'resign' ? '퇴사 처리 후에는 로그인하거나 복직할 수 없습니다. 기존 교육 및 수강 이력은 유지됩니다.' : '관리자 역할을 제거하면 관리 화면을 이용할 수 없습니다. 계속하시겠습니까?'} label={confirm === 'resign' ? '퇴사 처리' : '역할 제거'} pending={action.pending} error={action.error} onClose={() => setConfirm(null)} onConfirm={() => { if (confirm === 'resign') void changeStatus('RESIGNED'); else void changeRole('ADMIN', false) }} />}
    </>}
  </div>
}
