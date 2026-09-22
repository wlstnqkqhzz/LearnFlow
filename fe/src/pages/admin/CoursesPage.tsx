import { useCallback, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { courseApi } from '../../api/courseApi.ts'
import type { Course, CourseSearch, CourseStatus, CourseType } from '../../api/courseTypes.ts'
import { CourseStatusBadge, CourseTypeBadge } from '../../components/admin/CourseBadges.tsx'
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../../components/admin/AdminUI.tsx'
import { useRemote } from '../../hooks/useRemote.ts'
import { coursePeriod, courseStatuses, courseTypes } from './courseUtils.ts'

export function CourseTable({ courses, actionLabel = '상세 보기', actionPath = course => `/admin/courses/${course.id}` }: { courses: Course[]; actionLabel?: string; actionPath?: (course: Course) => string }) {
  return <div className="table-scroll" tabIndex={0} role="region" aria-label="교육과정 목록"><table className="admin-table course-table"><thead><tr><th scope="col">과정명</th><th scope="col">유형</th><th scope="col">강사</th><th scope="col">운영 기간</th><th scope="col">수료 기준</th><th scope="col">상태</th><th scope="col">관리</th></tr></thead><tbody>{courses.map(course => <tr key={course.id}><th scope="row">{course.title}<small className="table-secondary">과정 #{course.id}</small></th><td><CourseTypeBadge type={course.courseType} /></td><td>{course.instructorName ?? '강사 미지정'}</td><td>{coursePeriod(course)}</td><td>{course.passingProgressRate}%</td><td><CourseStatusBadge status={course.status} /></td><td><Link className="admin-button" to={actionPath(course)}>{actionLabel}</Link></td></tr>)}</tbody></table></div>
}

export function CoursesPage({ assignmentMode = false, examMode = false }: { assignmentMode?: boolean; examMode?: boolean }) {
  const [filters, setFilters] = useState<CourseSearch>({ page: 0, size: 20 })
  const [keyword, setKeyword] = useState('')
  const loader = useCallback((signal: AbortSignal) => courseApi.list(filters, signal), [filters])
  const query = useRemote(loader)
  const data = query.data
  const filtered = !!(filters.keyword || filters.status || filters.type || filters.instructorId)
  function filter(change: Partial<CourseSearch>) { setFilters(current => ({ ...current, ...change, page: 0 })) }
  function submit(event: FormEvent) { event.preventDefault(); filter({ keyword: keyword.trim() || undefined }) }
  return <div className="management-page">
    <PageHeader title={examMode ? '시험 관리' : assignmentMode ? '교육 배정' : '교육과정'} description={examMode ? '교육과정을 선택해 시험과 문항을 관리합니다.' : assignmentMode ? '교육과정 상세에서 자동 배정 규칙을 관리합니다.' : '교육과정을 생성하고 운영 상태를 관리합니다.'}>{!assignmentMode && !examMode && <Link className="admin-button primary-button" to="/admin/courses/new">+ 교육과정 만들기</Link>}</PageHeader>
    <form className="admin-toolbar" onSubmit={submit}>
      <label className="admin-field search-field">과정명 검색<input type="search" value={keyword} maxLength={200} onChange={event => setKeyword(event.target.value)} placeholder="교육과정 검색" /></label><button className="admin-button" type="submit">검색</button>
      <label className="admin-field">상태<select value={filters.status ?? ''} onChange={event => filter({ status: event.target.value as CourseStatus || undefined })}><option value="">전체</option>{Object.entries(courseStatuses).map(([value, item]) => <option key={value} value={value}>{item.label}</option>)}</select></label>
      <label className="admin-field">유형<select value={filters.type ?? ''} onChange={event => filter({ type: event.target.value as CourseType || undefined })}><option value="">전체</option>{Object.entries(courseTypes).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="admin-field">강사 회원 ID<input type="number" min="1" value={filters.instructorId ?? ''} onChange={event => filter({ instructorId: event.target.value ? Number(event.target.value) : undefined })} placeholder="전체" /></label>
      <button className="admin-button" type="button" onClick={() => { setKeyword(''); setFilters({ page: 0, size: filters.size }) }}>초기화</button>
    </form>
    <div className="surface admin-table-surface">{query.loading ? <LoadingState /> : query.error ? <ErrorState message={query.error} retry={query.reload} /> : !data?.content.length ? <EmptyState message={filtered ? '검색 조건에 맞는 교육과정이 없습니다.' : '등록된 교육과정이 없습니다.'}>{!filtered && !assignmentMode && !examMode && <Link className="admin-button primary-button" to="/admin/courses/new">교육과정 만들기</Link>}</EmptyState> : <CourseTable courses={data.content} actionLabel={examMode ? '시험 관리' : '상세 보기'} actionPath={examMode ? course => `/admin/courses/${course.id}/exam` : undefined} />}
      {data && <div className="admin-pagination"><span>전체 {data.totalElements}개 · {data.totalPages ? data.page + 1 : 0} / {data.totalPages} 페이지</span><div className="row-actions"><label>표시 개수 <select aria-label="페이지당 교육과정 수" value={filters.size} onChange={event => filter({ size: Number(event.target.value) })}>{[10, 20, 50, 100].map(size => <option key={size} value={size}>{size}</option>)}</select></label><button className="admin-button" disabled={data.page <= 0} onClick={() => setFilters(current => ({ ...current, page: current.page - 1 }))}>이전</button><button className="admin-button" disabled={data.page + 1 >= data.totalPages} onClick={() => setFilters(current => ({ ...current, page: current.page + 1 }))}>다음</button></div></div>}
    </div>
  </div>
}
