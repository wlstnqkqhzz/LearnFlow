import type { StatisticsFilter } from '../../api/statisticsTypes.ts'

export const statisticsPresets = { days30: '최근 30일', months3: '최근 3개월', months6: '최근 6개월', year: '올해', custom: '직접 날짜 선택' }
export type StatisticsPreset = keyof typeof statisticsPresets
export function seoulToday(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now)
  return ['year', 'month', 'day'].map(type => parts.find(part => part.type === type)!.value).join('-')
}
function date(value: string) { return new Date(`${value}T00:00:00Z`) }
function iso(value: Date) { return value.toISOString().slice(0, 10) }
export function presetDates(preset: Exclude<StatisticsPreset, 'custom'>, today = seoulToday()): StatisticsFilter {
  const start = date(today)
  if (preset === 'days30') start.setUTCDate(start.getUTCDate() - 29)
  else if (preset === 'year') start.setUTCMonth(0, 1)
  else {
    const day = start.getUTCDate()
    start.setUTCDate(1)
    start.setUTCMonth(start.getUTCMonth() - (preset === 'months3' ? 3 : 6))
    const lastDay = new Date(Date.UTC(start.getUTCFullYear(), start.getUTCMonth() + 1, 0)).getUTCDate()
    start.setUTCDate(Math.min(day, lastDay) + 1)
  }
  return { startDate: iso(start), endDate: today }
}
export function statisticsValidation(filter: StatisticsFilter, today = seoulToday()) {
  for (const value of [filter.startDate, filter.endDate]) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number.isNaN(date(value).getTime()) || iso(date(value)) !== value || value < '1000-01-01') return '올바른 시작일과 종료일을 입력해 주세요.'
  }
  if (filter.startDate > filter.endDate) return '시작일은 종료일보다 늦을 수 없습니다.'
  if (filter.endDate > today) return '종료일은 서울 기준 오늘 이후일 수 없습니다.'
  if ((date(filter.endDate).getTime() - date(filter.startDate).getTime()) / 86400000 >= 366) return '최대 366일까지 조회할 수 있습니다.'
  if ([filter.courseId, filter.departmentId].some(id => id !== undefined && (!Number.isSafeInteger(id) || id <= 0))) return '올바른 과정과 부서를 선택해 주세요.'
  return ''
}
export function readStatisticsQuery(params: URLSearchParams, today = seoulToday()) {
  const rawPreset = params.get('preset')
  const preset: StatisticsPreset = rawPreset && Object.hasOwn(statisticsPresets, rawPreset) ? rawPreset as StatisticsPreset : params.has('startDate') || params.has('endDate') ? 'custom' : 'days30'
  const defaults = presetDates(preset === 'custom' ? 'days30' : preset, today)
  const filter: StatisticsFilter = { startDate: params.get('startDate') ?? defaults.startDate, endDate: params.get('endDate') ?? defaults.endDate }
  for (const key of ['courseId', 'departmentId'] as const) if (params.has(key)) filter[key] = /^\d+$/.test(params.get(key)!) ? Number(params.get(key)) : NaN
  const page = (key: string) => { const raw = params.get(key) ?? '0'; const value = Number(raw); return /^\d+$/.test(raw) && Number.isSafeInteger(value) && value <= 2147483647 ? value : 0 }
  return { filter, preset, coursePage: page('coursePage'), departmentPage: page('departmentPage') }
}
export function statisticsQuery(filter: StatisticsFilter, preset: StatisticsPreset, coursePage = 0, departmentPage = 0) {
  const params = new URLSearchParams({ startDate: filter.startDate, endDate: filter.endDate, preset })
  for (const key of ['courseId', 'departmentId'] as const) if (filter[key] !== undefined) params.set(key, String(filter[key]))
  if (coursePage) params.set('coursePage', String(coursePage))
  if (departmentPage) params.set('departmentPage', String(departmentPage))
  return params
}
export const statisticNumber = (value: number | null, suffix = '') => value === null ? '—' : `${value.toLocaleString('ko-KR', { maximumFractionDigits: 2 })}${suffix}`
