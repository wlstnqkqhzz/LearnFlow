import type { EnrollmentStatus } from '../components/common/enrollmentStatus'

// UI 확인 전용 고정 데이터. 서로 다른 집계 기간의 지표이며 실제 통계가 아닙니다.
export const overview = {
  completionRate: 82, change: 4,
  metrics: [{ label: '전체 직원', value: 248, unit: '명' }, { label: '진행 중 교육', value: 12, unit: '개' }, { label: '수강 중', value: 186, unit: '건' }],
}
export const enrollmentFlow: { status: EnrollmentStatus; count: number }[] = [
  { status: 'ASSIGNED', count: 42 }, { status: 'IN_PROGRESS', count: 96 },
  { status: 'COMPLETED', count: 88 }, { status: 'FAILED', count: 9 }, { status: 'EXPIRED', count: 5 },
]
export const activeCourses = [
  { id: 1, title: '정보보안 필수교육 2026', type: '필수교육', learners: 184, progress: 71 },
  { id: 2, title: '개인정보보호 교육', type: '필수교육', learners: 152, progress: 88 },
  { id: 3, title: '직장 내 괴롭힘 예방교육', type: '필수교육', learners: 168, progress: 64 },
  { id: 4, title: '신입사원 온보딩', type: '필수교육', learners: 26, progress: 42 },
]
export const attentionItems = [
  { label: '마감 임박', description: '3일 이내 마감', count: 3, tone: 'warning' },
  { label: '실패', description: '재수강 필요', count: 9, tone: 'danger' },
  { label: '만료', description: '기간 초과', count: 5, tone: 'neutral' },
]
export const recentCompletions = [
  { id: 1, name: '김민수', course: '정보보안 필수교육 2026' },
  { id: 2, name: '이서연', course: '개인정보보호 교육' },
  { id: 3, name: '최지우', course: '신입사원 온보딩' },
]
export const recentActivities: { id: number; name: string; department: string; course: string; status: EnrollmentStatus; date: string }[] = [
  { id: 1, name: '김민수', department: '개발본부', course: '정보보안 필수교육 2026', status: 'IN_PROGRESS', date: '2026-09-20' },
  { id: 2, name: '이서연', department: '인사팀', course: '개인정보보호 교육', status: 'COMPLETED', date: '2026-09-19' },
  { id: 3, name: '박준호', department: '영업1팀', course: '직장 내 괴롭힘 예방교육', status: 'ASSIGNED', date: '2026-09-19' },
  { id: 4, name: '최지우', department: '마케팅팀', course: '신입사원 온보딩', status: 'IN_PROGRESS', date: '2026-09-18' },
  { id: 5, name: '정하늘', department: '재무팀', course: '정보보안 필수교육 2026', status: 'FAILED', date: '2026-09-17' },
  { id: 6, name: '강도현', department: '개발본부', course: '개인정보보호 교육', status: 'EXPIRED', date: '2026-09-15' },
]
