import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { memberApi } from '../../api/memberApi'
import type { Department, JobPosition } from '../../api/adminTypes'
import { Modal } from '../../components/admin/Modal'
import { OrganizationSelect } from '../../components/admin/OrganizationSelect'
import { Feedback, SubmitButton } from '../../components/admin/AdminUI'
import { useAction } from '../../hooks/useAction'

export function MemberCreateModal({ departments, jobs, onClose }: { departments: Department[]; jobs: JobPosition[]; onClose: () => void }) {
  const navigate = useNavigate()
  const action = useAction()
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    await action.run(async () => {
      const member = await memberApi.create({ employeeNumber: String(data.get('employeeNumber')).trim(), name: String(data.get('name')).trim(), email: String(data.get('email')).trim(), password: String(data.get('password')), hireDate: String(data.get('hireDate')), departmentId: Number(data.get('departmentId')), jobPositionId: Number(data.get('jobPositionId')) })
      form.reset()
      navigate(`/admin/members/${member.id}`, { state: { created: true } })
    }, '회원을 등록했습니다.')
    // 실패 시에도 비밀번호 원문은 폼에 유지하지 않는다.
    const password = form.elements.namedItem('password')
    if (password instanceof HTMLInputElement) password.value = ''
  }
  return <Modal title="회원 등록" busy={action.pending} onClose={onClose}><form className="admin-form" onSubmit={submit}><fieldset className="form-grid" disabled={action.pending}>
    <label className="admin-field">사번<input name="employeeNumber" required maxLength={50} pattern=".*\S.*" /></label>
    <label className="admin-field">이름<input name="name" required maxLength={100} pattern=".*\S.*" autoComplete="off" /></label>
    <label className="admin-field">이메일<input name="email" type="email" required maxLength={255} autoComplete="off" /></label>
    <label className="admin-field">초기 비밀번호<input name="password" type="password" required minLength={8} maxLength={128} pattern=".*\S.*" autoComplete="new-password" /><small>8~128자. 등록 요청에만 사용됩니다.</small></label>
    <OrganizationSelect name="departmentId" label="부서" items={departments} />
    <OrganizationSelect name="jobPositionId" label="직무" items={jobs} />
    <label className="admin-field">입사일<input name="hireDate" type="date" required /></label>
  </fieldset><p className="admin-hint">직원 역할로 등록됩니다. 추가 역할은 등록 후 상세 화면에서 관리하세요.</p><Feedback error={action.error} /><div className="admin-actions"><SubmitButton pending={action.pending} label="등록" /></div></form></Modal>
}
