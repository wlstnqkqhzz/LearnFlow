import { selectableOrganizations } from '../../pages/admin/adminUtils'

export function OrganizationSelect({ label, name, items, currentId }: { label: string; name: string; items: { id: number; name: string; isActive: boolean }[]; currentId?: number }) {
  const candidates = selectableOrganizations(items, currentId)
  return <label className="admin-field">{label}<select name={name} required defaultValue={currentId ?? ''}><option value="" disabled>선택하세요</option>{currentId && !items.some(item => item.id === currentId) && <option value={currentId}>미확인 항목 #{currentId} (현재 값)</option>}{candidates.map(item => <option key={item.id} value={item.id}>{item.name}{!item.isActive ? ' (비활성·현재 값)' : ''}</option>)}</select><small>신규 선택은 활성 항목만 가능합니다.</small></label>
}
