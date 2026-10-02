import { useCallback, useState, type FormEvent } from 'react'
import { courseApi } from '../../api/courseApi.ts'
import { departmentApi } from '../../api/departmentApi.ts'
import type { StatisticsFilter } from '../../api/statisticsTypes.ts'
import { useRemote } from '../../hooks/useRemote.ts'
import { StatisticsPagination, StatisticsState } from './StatisticsViews.tsx'
import { presetDates, seoulToday, statisticsPresets, statisticsValidation, type StatisticsPreset } from './statisticsUtils.ts'

function CourseFilter({ value, onChange }: { value?: number; onChange: (value?: number) => void }) {
  const [keyword, setKeyword] = useState('')
  const [search, setSearch] = useState({ page: 0, size: 20, keyword: '' })
  const query = useRemote(useCallback((signal: AbortSignal) => courseApi.list(search, signal), [search]))
  const runSearch = () => setSearch({ ...search, page: 0, keyword: keyword.trim() })
  return <details className="statistics-course-picker"><summary>과정 선택 {value ? `· #${value}` : '· 전체'}</summary>
    <div className="admin-toolbar"><label className="admin-field search-field">과정명 검색<input value={keyword} maxLength={200} onChange={event => setKeyword(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); runSearch() } }} /></label><button type="button" className="admin-button" onClick={runSearch}>과정 검색</button></div>
    <label className="admin-field">교육과정<select value={value ?? ''} onChange={event => onChange(event.target.value ? Number(event.target.value) : undefined)}>
      <option value="">전체 과정</option>
      {value !== undefined && !query.data?.content.some(course => course.id === value) && <option value={value}>선택한 과정 #{value}</option>}
      {query.data?.content.map(course => <option value={course.id} key={course.id}>{course.title} · #{course.id}</option>)}
    </select></label>
    <StatisticsState loading={query.loading} error={query.error} retry={query.reload}>{query.data?.content.length === 0 && <p className="admin-hint">검색된 과정이 없습니다.</p>}</StatisticsState>
    {query.data && <StatisticsPagination data={query.data} onPage={page => setSearch({ ...search, page })} />}
  </details>
}
export function StatisticsFilters({ initial, initialPreset, onApply }: { initial: StatisticsFilter; initialPreset: StatisticsPreset; onApply: (filter: StatisticsFilter, preset: StatisticsPreset) => void }) {
  const [filter, setFilter] = useState(initial)
  const [preset, setPreset] = useState(initialPreset)
  const [error, setError] = useState('')
  const departments = useRemote(useCallback((signal: AbortSignal) => departmentApi.list(false, signal), []))
  const change = (patch: Partial<StatisticsFilter>) => setFilter(current => ({ ...current, ...patch }))
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const invalid = statisticsValidation(filter)
    setError(invalid)
    if (!invalid) onApply(filter, preset)
  }
  return <form className="surface statistics-filters" onSubmit={submit} aria-label="통계 조회 필터">
    <div className="admin-toolbar">
      <label className="admin-field">조회 기간<select value={preset} onChange={event => { const next = event.target.value as StatisticsPreset; setPreset(next); if (next !== 'custom') change(presetDates(next)) }}>
        {Object.entries(statisticsPresets).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
      </select></label>
      <label className="admin-field">시작일<input type="date" required min="1000-01-01" max={seoulToday()} value={filter.startDate} onInput={event => { setPreset('custom'); change({ startDate: event.currentTarget.value }) }} /></label>
      <label className="admin-field">종료일<input type="date" required min="1000-01-01" max={seoulToday()} value={filter.endDate} onInput={event => { setPreset('custom'); change({ endDate: event.currentTarget.value }) }} /></label>
      <label className="admin-field">부서<select value={filter.departmentId ?? ''} onChange={event => change({ departmentId: event.target.value ? Number(event.target.value) : undefined })}>
        <option value="">전체 부서</option>
        {filter.departmentId !== undefined && !departments.data?.some(department => department.id === filter.departmentId) && <option value={filter.departmentId}>선택한 부서 #{filter.departmentId}</option>}
        {departments.data?.map(department => <option key={department.id} value={department.id}>{department.name}{department.isActive ? '' : ' (비활성)'}</option>)}
      </select></label>
      <button type="submit" className="admin-button primary-button">통계 조회</button>
    </div>
    <p className="admin-hint">Asia/Seoul 기준 · 시작일·종료일 포함 · 최대 366일 · 필터를 변경한 뒤 통계 조회를 눌러 주세요.</p>
    {departments.loading && <p className="admin-hint" role="status">부서 선택 목록을 불러오고 있습니다…</p>}
    {departments.error && <div role="alert">부서 선택 목록 조회 실패 <button className="admin-button" type="button" onClick={departments.reload}>부서 다시 시도</button></div>}
    <CourseFilter value={filter.courseId} onChange={courseId => change({ courseId })} />
    {error && <p className="admin-error" role="alert">{error}</p>}
  </form>
}
