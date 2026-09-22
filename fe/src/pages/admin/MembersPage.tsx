import { useCallback, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { memberApi } from '../../api/memberApi'
import type { MemberSearch, MemberStatus } from '../../api/adminTypes'
import { useRemote } from '../../hooks/useRemote'
import { EmptyState, ErrorState, LoadingState, MemberBadge, PageHeader, RoleBadges } from '../../components/admin/AdminUI'
import { memberStatuses, organizationName } from './adminUtils'
import { loadOrganizationOptions } from './organizationOptions'
import { MemberCreateModal } from './MemberCreateModal'

export function MembersPage() {
  const [filters, setFilters] = useState<MemberSearch>({ page: 0, size: 20 })
  const [search, setSearch] = useState('')
  const [creating, setCreating] = useState(false)
  const options = useRemote(loadOrganizationOptions)
  const loader = useCallback((signal: AbortSignal) => memberApi.list(filters, signal), [filters])
  const query = useRemote(loader)
  function submitSearch(event: FormEvent) { event.preventDefault(); setFilters({ ...filters, page: 0, name: search.trim() || undefined }) }
  function filter(change: Partial<MemberSearch>) { setFilters({ ...filters, ...change, page: 0 }) }
  const filtered = !!(filters.name || filters.status || filters.departmentId || filters.jobPositionId)
  const data = query.data
  return <div className="management-page">
    <PageHeader title="회원 관리" description="조직 구성원의 정보, 재직 상태와 역할을 관리합니다."><button type="button" className="admin-button primary-button" disabled={!options.data} onClick={() => setCreating(true)}>+ 회원 등록</button></PageHeader>
    {options.error && <ErrorState message={`조직 선택 목록: ${options.error}`} retry={options.reload} />}
    <form className="admin-toolbar" onSubmit={submitSearch}>
      <label className="admin-field search-field">이름 검색<input type="search" value={search} maxLength={100} onChange={event => setSearch(event.target.value)} placeholder="회원 이름 검색" /></label><button type="submit" className="admin-button">검색</button>
      <label className="admin-field">재직 상태<select value={filters.status ?? ''} onChange={event => filter({ status: event.target.value as MemberStatus || undefined })}><option value="">전체</option>{Object.entries(memberStatuses).map(([key, value]) => <option key={key} value={key}>{value.label}</option>)}</select></label>
      <label className="admin-field">부서<select disabled={!options.data} value={filters.departmentId ?? ''} onChange={event => filter({ departmentId: event.target.value ? Number(event.target.value) : undefined })}><option value="">전체 부서</option>{options.data?.departments.map(item => <option key={item.id} value={item.id}>{item.name}{!item.isActive ? ' (비활성)' : ''}</option>)}</select></label>
      <label className="admin-field">직무<select disabled={!options.data} value={filters.jobPositionId ?? ''} onChange={event => filter({ jobPositionId: event.target.value ? Number(event.target.value) : undefined })}><option value="">전체 직무</option>{options.data?.jobs.map(item => <option key={item.id} value={item.id}>{item.name}{!item.isActive ? ' (비활성)' : ''}</option>)}</select></label>
      <button className="admin-button" type="button" onClick={() => { setSearch(''); setFilters({ page: 0, size: filters.size }) }}>초기화</button>
    </form>
    <div className="surface admin-table-surface">{query.loading ? <LoadingState /> : query.error ? <ErrorState message={query.error} retry={query.reload} /> : !data?.content.length ? <EmptyState message={filtered ? '검색 조건에 맞는 회원이 없습니다.' : '등록된 회원이 없습니다.'}>{data && data.page > 0 && <button className="admin-button" onClick={() => filter({})}>첫 페이지로</button>}</EmptyState> : <div className="table-scroll" tabIndex={0} role="region" aria-label="회원 목록"><table className="admin-table member-table"><thead><tr>{['회원', '사번', '부서', '직무', '역할', '상태', '입사일', '관리'].map(label => <th key={label} scope="col">{label}</th>)}</tr></thead><tbody>{data.content.map(member => <tr key={member.id}><th scope="row">{member.name}<small className="table-secondary">{member.email}</small></th><td>{member.employeeNumber}</td><td>{organizationName(options.data?.departments ?? [], member.departmentId)}</td><td>{organizationName(options.data?.jobs ?? [], member.jobPositionId)}</td><td><RoleBadges roles={member.roles} /></td><td><MemberBadge status={member.status} /></td><td>{member.hireDate}</td><td><Link className="admin-button" to={`/admin/members/${member.id}`}>상세 보기</Link></td></tr>)}</tbody></table></div>}
      {data && <div className="admin-pagination"><span>전체 {data.totalElements}명 · {data.totalPages ? data.page + 1 : 0} / {data.totalPages} 페이지</span><div className="row-actions"><label>표시 개수 <select aria-label="페이지당 회원 수" value={filters.size} onChange={event => filter({ size: Number(event.target.value) })}>{[10, 20, 50, 100].map(size => <option key={size} value={size}>{size}</option>)}</select></label><button className="admin-button" disabled={data.page <= 0} onClick={() => setFilters({ ...filters, page: filters.page - 1 })}>이전</button><button className="admin-button" disabled={data.page + 1 >= data.totalPages} onClick={() => setFilters({ ...filters, page: filters.page + 1 })}>다음</button></div></div>}
    </div>
    {creating && options.data && <MemberCreateModal departments={options.data.departments} jobs={options.data.jobs} onClose={() => setCreating(false)} />}
  </div>
}
