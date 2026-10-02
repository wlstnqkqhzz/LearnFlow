import { useId, useState } from 'react'
import type { StatisticsTrends } from '../../api/statisticsTypes.ts'
import { EmptyState } from '../../components/admin/AdminUI.tsx'
import { statisticNumber as number } from './statisticsUtils.ts'

export function StatisticsChart({ data }: { data: StatisticsTrends }) {
  const titleId = useId()
  const [selected, setSelected] = useState<number | null>(null)
  const { points, granularity } = data
  const label = (value: string) => granularity === 'MONTH' ? value.slice(0, 7) : value
  const hasActivity = points.some(point => point.assignments !== 0 || point.completions !== 0)
  // Only visual coordinates are calculated here; all counts and buckets come from the API.
  const max = Math.max(1, ...points.flatMap(point => [point.assignments, point.completions]))
  const x = (index: number) => points.length === 1 ? 386 : 72 + index / (points.length - 1) * 628
  const y = (count: number) => 220 - count / max * 180
  const active = selected === null ? undefined : points[selected]
  return <div className="statistics-chart">
    <p className="admin-hint">{granularity === 'DAY' ? '일별' : '월별'} · 배정일 / 수료일 기준 · 첫·마지막 구간은 선택 기간 안의 실적만 포함</p>
    <div className="statistics-legend"><span className="statistics-assignment">━ 배정</span><span className="statistics-completion">┄ 수료</span></div>
    {!hasActivity ? <EmptyState message="선택 기간에 배정·수료 추세 데이터가 없습니다." /> : <>
      <svg viewBox="0 0 740 270" role="group" aria-labelledby={titleId}>
        <title id={titleId}>{`배정 및 수료 ${granularity === 'DAY' ? '일별' : '월별'} 추세. 각 날짜에 초점을 두거나 아래 데이터 표를 펼쳐 값을 확인하세요.`}</title>
        <text x="12" y="20">건수</text>
        {[0, 0.5, 1].map(ratio => <g key={ratio}><line className="statistics-grid" x1="72" x2="700" y1={y(max * ratio)} y2={y(max * ratio)} /><text x="62" y={y(max * ratio) + 4} textAnchor="end">{number(max * ratio)}</text></g>)}
        <path className="statistics-axis" d="M72 40V220H700" fill="none" />
        {(['assignments', 'completions'] as const).map(series => <polyline key={series} className={`statistics-line ${series}`} fill="none" points={points.map((point, index) => `${x(index)},${y(point[series])}`).join(' ')} />)}
        {points.map((point, index) => <g key={point.bucketStart} tabIndex={0} role="img" aria-label={`${label(point.bucketStart)} · ${point.periodStart} ~ ${point.periodEnd}: 배정 ${point.assignments}건, 수료 ${point.completions}건`} onFocus={() => setSelected(index)} onBlur={() => setSelected(null)} onMouseEnter={() => setSelected(index)} onMouseLeave={() => setSelected(null)}>
          <title>{`${label(point.bucketStart)}: 배정 ${point.assignments}건, 수료 ${point.completions}건`}</title>
          <rect x={x(index) - Math.min(15, 314 / Math.max(points.length, 1))} y="35" width={Math.min(30, 628 / Math.max(points.length, 1))} height="190" fill="transparent" />
          <circle className="statistics-dot assignments" cx={x(index)} cy={y(point.assignments)} r="4" />
          <circle className="statistics-dot completions" cx={x(index)} cy={y(point.completions)} r="3" />
          {(index === 0 || index === points.length - 1 || points.length > 3 && index === Math.floor(points.length / 2)) && <text x={x(index)} y="246" textAnchor={index === 0 ? 'start' : index === points.length - 1 ? 'end' : 'middle'}>{label(point.bucketStart)}</text>}
        </g>)}
      </svg>
      <p className="statistics-chart-value" aria-live="polite">{active ? `${label(active.bucketStart)} (${active.periodStart} ~ ${active.periodEnd}) · 배정 ${number(active.assignments)}건 · 수료 ${number(active.completions)}건` : '날짜에 마우스를 올리거나 Tab 키로 이동하면 값을 확인할 수 있습니다.'}</p>
    </>}
    {!!points.length && <details><summary>추세 데이터 표</summary><div className="table-scroll" role="region" aria-label="추세 데이터 표" tabIndex={0}><table className="admin-table"><thead><tr><th scope="col">날짜 / 월</th><th scope="col">실제 집계 기간</th><th scope="col">배정</th><th scope="col">수료</th></tr></thead><tbody>{points.map(point => <tr key={point.bucketStart}><th scope="row">{label(point.bucketStart)}</th><td>{point.periodStart} ~ {point.periodEnd}</td><td>{number(point.assignments)}</td><td>{number(point.completions)}</td></tr>)}</tbody></table></div></details>}
  </div>
}
