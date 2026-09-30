import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { PageResponse } from '../../api/adminTypes.ts'
import type { Course } from '../../api/courseTypes.ts'
import type { RetrainingPolicy } from '../../api/retrainingTypes.ts'
import { EmptyState, ErrorState, LoadingState } from '../../components/admin/AdminUI.tsx'
import { CourseStatusBadge } from '../../components/admin/CourseBadges.tsx'
import { coursePeriod } from './courseUtils.ts'

export function RetrainingState({ loading, error, empty, emptyMessage, retry, children }: {
  loading: boolean; error: string; empty: boolean; emptyMessage: string; retry: () => void; children: ReactNode
}) {
  return loading ? <LoadingState /> : error ? <ErrorState message={error} retry={retry} />
    : empty ? <EmptyState message={emptyMessage} /> : <>{children}</>
}

export function RetrainingPagination({ data, onPage, disabled = false }: {
  data: Pick<PageResponse<unknown>, 'page' | 'totalPages' | 'totalElements'>; onPage: (page: number) => void; disabled?: boolean
}) {
  return <div className="admin-pagination"><span>전체 {data.totalElements}개 · {data.totalPages ? data.page + 1 : 0} / {data.totalPages} 페이지</span>
    <div className="row-actions"><button type="button" className="admin-button" disabled={disabled || data.page <= 0} onClick={() => onPage(data.page - 1)}>이전</button>
      <button type="button" className="admin-button" disabled={disabled || data.page + 1 >= data.totalPages} onClick={() => onPage(data.page + 1)}>다음</button></div></div>
}

export function PolicyBadge({ enabled }: { enabled: boolean }) {
  return <span className={`status-badge tone-${enabled ? 'success' : 'neutral'}`}>{enabled ? '활성' : '중지'}</span>
}

export function AutomationHelp() {
  return <div className="admin-hint"><p>자동 생성: 예정일 도래 시 새 교육과정을 자동 생성합니다.</p>
    <p>자동 OPEN: 새 교육과정 생성 직후 OPEN 및 기존 자동 배정을 실행합니다. 시작일에 자동 OPEN하는 기능이 아닙니다.</p></div>
}

export function PolicyTable({ policies }: { policies: RetrainingPolicy[] }) {
  return <div className="table-scroll" tabIndex={0} role="region" aria-label="재교육 정책 목록"><table className="admin-table retraining-table"><thead><tr>
    <th scope="col">정책 / 기준 과정</th><th scope="col">반복 일정</th><th scope="col">다음 회차 / 생성 예정일</th><th scope="col">자동화</th><th scope="col">상태</th><th scope="col">관리</th>
  </tr></thead><tbody>{policies.map(policy => <tr key={policy.id}>
    <th scope="row">{policy.baseTitle}<small className="table-secondary"><Link to={`/admin/courses/${policy.sourceCourseId}`}>기준 과정 #{policy.sourceCourseId}</Link></small></th>
    <td>{policy.intervalMonths}개월 주기<small className="table-secondary">첫 시작 {policy.firstStartDate}<br />운영 {policy.durationDays}일 · 생성 {policy.generationLeadDays}일 전</small></td>
    <td>{policy.nextOccurrenceNumber}회차<small className="table-secondary">{policy.nextGenerationDate}</small></td>
    <td>자동 생성 {policy.autoCreate ? '사용' : '미사용'}<small className="table-secondary">자동 OPEN {policy.autoOpen ? '사용' : '미사용'}</small></td>
    <td><PolicyBadge enabled={policy.enabled} /></td><td><Link className="admin-button" to={`/admin/retraining-policies/${policy.id}`}>상세 보기</Link></td>
  </tr>)}</tbody></table></div>
}

export function PolicySummary({ policy }: { policy: RetrainingPolicy }) {
  return <dl className="enrollment-detail">
    <div><dt>기본 제목</dt><dd>{policy.baseTitle}</dd></div>
    <div><dt>기준 과정</dt><dd><Link to={`/admin/courses/${policy.sourceCourseId}`}>교육과정 #{policy.sourceCourseId} 상세 보기</Link></dd></div>
    <div><dt>상태</dt><dd><PolicyBadge enabled={policy.enabled} /></dd></div>
    <div><dt>반복 일정</dt><dd>{policy.intervalMonths}개월 주기 · 첫 시작 {policy.firstStartDate}</dd></div>
    <div><dt>운영 기간</dt><dd>{policy.durationDays}일 · 생성 선행 {policy.generationLeadDays}일</dd></div>
    <div><dt>다음 회차</dt><dd>{policy.nextOccurrenceNumber}회차 · 생성 예정일 {policy.nextGenerationDate} (Asia/Seoul)</dd></div>
    <div><dt>자동화</dt><dd>자동 생성 {policy.autoCreate ? '사용' : '미사용'} · 자동 OPEN {policy.autoOpen ? '사용' : '미사용'}</dd></div>
  </dl>
}

export function OccurrenceTable({ courses }: { courses: Course[] }) {
  return <div className="table-scroll" tabIndex={0} role="region" aria-label="생성된 회차 목록"><table className="admin-table"><thead><tr>
    <th scope="col">회차</th><th scope="col">교육과정</th><th scope="col">운영 기간</th><th scope="col">상태</th>
  </tr></thead><tbody>{courses.map(course => <tr key={course.id}><td>{course.occurrenceNumber ?? '—'}회차</td>
    <th scope="row"><Link to={`/admin/courses/${course.id}`}>{course.title}</Link></th><td>{coursePeriod(course)}</td><td><CourseStatusBadge status={course.status} /></td>
  </tr>)}</tbody></table></div>
}
