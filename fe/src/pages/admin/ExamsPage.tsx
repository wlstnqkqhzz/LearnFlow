import { CoursesPage } from './CoursesPage.tsx'

// 전체 Exam 목록 API가 없으므로 Course 목록에서 과정별 시험 관리로 진입한다.
export function ExamsPage() { return <CoursesPage examMode /> }
