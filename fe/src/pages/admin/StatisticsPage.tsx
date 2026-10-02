import { useCallback, useEffect, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { statisticsApi } from '../../api/statisticsApi.ts'
import type { StatisticsFilter } from '../../api/statisticsTypes.ts'
import { PageHeader } from '../../components/admin/AdminUI.tsx'
import { useRemote } from '../../hooks/useRemote.ts'
import { StatisticsChart } from './StatisticsChart.tsx'
import { StatisticsFilters } from './StatisticsFilters.tsx'
import { StatisticsCourseTable, StatisticsDepartmentTable, StatisticsOverviewView, StatisticsPagination, StatisticsState } from './StatisticsViews.tsx'
import { readStatisticsQuery, statisticsQuery, statisticsValidation } from './statisticsUtils.ts'
import './statistics.css'

function StatisticsResults({ filter, coursePage, departmentPage, onPage }: { filter: StatisticsFilter; coursePage: number; departmentPage: number; onPage: (section: 'coursePage' | 'departmentPage', page: number) => void }) {
  const overview = useRemote(useCallback((signal: AbortSignal) => statisticsApi.overview(filter, signal), [filter]))
  const trends = useRemote(useCallback((signal: AbortSignal) => statisticsApi.trends(filter, signal), [filter]))
  const courses = useRemote(useCallback((signal: AbortSignal) => statisticsApi.courses(filter, coursePage, signal), [filter, coursePage]))
  const departments = useRemote(useCallback((signal: AbortSignal) => statisticsApi.departments(filter, departmentPage, signal), [filter, departmentPage]))
  return <>
    <section className="surface statistics-section" aria-labelledby="statistics-overview"><h2 id="statistics-overview">전체 교육 통계</h2>
      <StatisticsState loading={overview.loading} error={overview.error} retry={overview.reload}>{overview.data && <StatisticsOverviewView data={overview.data} />}</StatisticsState>
    </section>
    <section className="surface statistics-section" aria-labelledby="statistics-trends"><h2 id="statistics-trends">배정·수료 추세</h2>
      <StatisticsState loading={trends.loading} error={trends.error} retry={trends.reload}>{trends.data && <StatisticsChart data={trends.data} />}</StatisticsState>
    </section>
    <section className="surface statistics-section" aria-labelledby="statistics-courses"><h2 id="statistics-courses">과정별 통계</h2>
      <p className="admin-hint">선택 기간에 배정된 교육의 현재 상태 · 배정 건수는 재교육 회차별로 구분합니다.</p>
      <p className="admin-hint">시험 통계는 선택 기간의 제출일 기준이며 재응시를 각각 포함합니다. 수강 상태 집계와 모집단이 다릅니다.</p>
      <StatisticsState loading={courses.loading} error={courses.error} retry={courses.reload}>{courses.data && <><StatisticsCourseTable rows={courses.data.data.content} /><StatisticsPagination data={courses.data.data} onPage={page => onPage('coursePage', page)} /></>}</StatisticsState>
    </section>
    <section className="surface statistics-section" aria-labelledby="statistics-departments"><h2 id="statistics-departments">부서별 통계</h2>
      <p className="admin-hint">현재 직속 부서 기준 · 하위 부서 미포함</p>
      <p className="admin-hint">교육 실적은 기간 내 배정 집단의 현재 상태이며 퇴사자의 실적도 포함합니다. 현재 직원 수는 재직·휴직 EMPLOYEE이며 조회 기간·과정과 무관합니다.</p>
      <StatisticsState loading={departments.loading} error={departments.error} retry={departments.reload}>{departments.data && <><StatisticsDepartmentTable rows={departments.data.data.content} /><StatisticsPagination data={departments.data.data} onPage={page => onPage('departmentPage', page)} /></>}</StatisticsState>
    </section>
  </>
}
export function StatisticsPage() {
  const [params, setParams] = useSearchParams()
  const query = params.toString()
  const applied = useMemo(() => readStatisticsQuery(new URLSearchParams(query)), [query])
  // Keep the request object stable when only a table page changes.
  const { startDate, endDate, courseId, departmentId } = applied.filter
  const filter = useMemo(() => ({ startDate, endDate, courseId, departmentId }), [startDate, endDate, courseId, departmentId])
  const invalid = statisticsValidation(filter)
  useEffect(() => {
    if (!params.has('startDate') || !params.has('endDate')) setParams(statisticsQuery(filter, applied.preset, applied.coursePage, applied.departmentPage), { replace: true })
  }, [params, setParams, filter, applied.preset, applied.coursePage, applied.departmentPage])
  const onPage = (section: 'coursePage' | 'departmentPage', page: number) => {
    const next = new URLSearchParams(params)
    next.set(section, String(page))
    setParams(next)
  }
  return <div className="management-page statistics-page">
    <PageHeader title="교육 통계" description="기간·과정·현재 부서별 교육 운영 성과를 확인합니다." />
    <StatisticsFilters key={`${startDate}:${endDate}:${courseId}:${departmentId}:${applied.preset}`} initial={filter} initialPreset={applied.preset} onApply={(next, preset) => setParams(statisticsQuery(next, preset))} />
    {invalid ? <p className="admin-error" role="alert">{invalid} 필터를 수정한 뒤 다시 조회해 주세요.</p> : <>
      <p className="statistics-applied" role="status">적용 기간: {startDate} ~ {endDate} (Asia/Seoul) · {courseId ? `과정 #${courseId}` : '전체 과정'} · {departmentId ? `부서 #${departmentId}` : '전체 부서'}</p>
      <StatisticsResults filter={filter} coursePage={applied.coursePage} departmentPage={applied.departmentPage} onPage={onPage} />
    </>}
  </div>
}
