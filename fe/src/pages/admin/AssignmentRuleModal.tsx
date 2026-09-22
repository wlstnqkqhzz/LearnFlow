import { useState, type FormEvent } from 'react'
import type { Department, JobPosition } from '../../api/adminTypes.ts'
import { assignmentRuleApi } from '../../api/assignmentRuleApi.ts'
import type { AssignmentRule, AssignmentRuleType } from '../../api/assignmentTypes.ts'
import type { CourseStatus } from '../../api/courseTypes.ts'
import { Feedback, SubmitButton } from '../../components/admin/AdminUI.tsx'
import { Modal } from '../../components/admin/Modal.tsx'
import { useAction } from '../../hooks/useAction.ts'
import { ruleRequest, ruleTypes } from './assignmentUtils.ts'

export function AssignmentRuleModal({ courseId, courseStatus, rule, departments, jobs, onClose, onSaved }: { courseId: number; courseStatus: CourseStatus; rule?: AssignmentRule; departments: Department[]; jobs: JobPosition[]; onClose: () => void; onSaved: () => void }) {
  const [type, setType] = useState<AssignmentRuleType>(rule?.ruleType ?? 'ALL_EMPLOYEES')
  const action = useAction()
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget)
    const value = type === 'DEPARTMENT' ? Number(data.get('departmentId')) : type === 'JOB_POSITION' ? Number(data.get('jobPositionId')) : type === 'NEW_EMPLOYEE' ? Number(data.get('newEmployeeDays')) : undefined
    const request = ruleRequest(type, value)
    const saved = await action.run(async () => { if (rule) await assignmentRuleApi.update(courseId, rule.id, request); else await assignmentRuleApi.create(courseId, { ...request, active: data.get('active') === 'on' }) }, rule ? '배정 규칙을 수정했습니다.' : '배정 규칙을 추가했습니다.')
    if (saved) onSaved()
  }
  const activeDepartments = departments.filter(item => item.isActive || item.id === rule?.departmentId)
  const activeJobs = jobs.filter(item => item.isActive || item.id === rule?.jobPositionId)
  return <Modal title={rule ? '배정 규칙 수정' : '배정 규칙 추가'} busy={action.pending} onClose={onClose}><form className="admin-form" onSubmit={event => void submit(event)}><Feedback error={action.error} /><fieldset disabled={action.pending}>
    <label className="admin-field">배정 대상 유형<select name="ruleType" value={type} onChange={event => setType(event.target.value as AssignmentRuleType)}>{Object.entries(ruleTypes).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
    {type === 'DEPARTMENT' && <label className="admin-field">대상 부서<select name="departmentId" required defaultValue={rule?.departmentId ?? ''}><option value="" disabled>부서 선택</option>{activeDepartments.map(item => <option key={item.id} value={item.id}>{item.name}{!item.isActive ? ' (비활성·현재 값)' : ''}</option>)}</select><small>해당 부서에 직접 소속된 재직 직원만 대상입니다.</small></label>}
    {type === 'JOB_POSITION' && <label className="admin-field">대상 직무<select name="jobPositionId" required defaultValue={rule?.jobPositionId ?? ''}><option value="" disabled>직무 선택</option>{activeJobs.map(item => <option key={item.id} value={item.id}>{item.name}{!item.isActive ? ' (비활성·현재 값)' : ''}</option>)}</select></label>}
    {type === 'NEW_EMPLOYEE' && <label className="admin-field">입사 후 일수<input name="newEmployeeDays" type="number" min="1" max="32767" required defaultValue={rule?.newEmployeeDays ?? 90} /><small>입사일부터 설정한 기간 이내인 재직 직원이 대상입니다.</small></label>}
    {type === 'ALL_EMPLOYEES' && <p className="admin-hint">현재 재직 중인 전체 직원이 자동 배정 대상입니다.</p>}
    {!rule && <label className="check-field"><input name="active" type="checkbox" defaultChecked /> 생성 즉시 활성화</label>}
  </fieldset>{courseStatus === 'OPEN' && <p className="admin-hint">운영 중인 과정입니다. 활성 규칙을 저장하면 조건에 맞는 미배정 직원에게 교육이 자동 배정될 수 있습니다.</p>}<div className="admin-actions"><button type="button" className="admin-button" onClick={onClose} disabled={action.pending}>취소</button><SubmitButton pending={action.pending} label="규칙 저장" /></div></form></Modal>
}
