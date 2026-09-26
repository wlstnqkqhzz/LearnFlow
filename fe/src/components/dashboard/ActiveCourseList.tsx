import type { Dashboard } from '../../api/dashboardApi'
import { Link } from 'react-router-dom'
import { Icon } from '../common/Icon'
import { ProgressBar } from '../common/Progress'

export function ActiveCourseList({ activeCourses }: Pick<Dashboard, 'activeCourses'>) {
  return (
    <section aria-labelledby="active-courses-title">
      <div className="section-heading">
        <div><h2 id="active-courses-title">운영 중인 교육</h2><p className="section-description">운영 중인 과정의 수료 현황</p></div>
        <Link className="text-button" to="/admin/courses">전체 보기 <Icon name="arrow" /></Link>
      </div>
      <ul className="course-list surface">
        {activeCourses.length === 0 && <li className="course-row">현재 운영 중인 교육이 없습니다.</li>}
        {activeCourses.map((course) => (
          <li className="course-row" key={course.courseId}>
            <span className="course-icon"><Icon name="book" /></span>
            <div className="course-copy"><h3>{course.title}</h3><p>{course.type === 'MANDATORY' ? '필수교육' : '선택교육'}<span aria-hidden="true"> · </span>수강 {course.enrollmentCount}명</p></div>
            <div className="course-progress"><ProgressBar value={course.completionRate} label={`${course.title} 수료율`} /><span>{course.completionRate}%</span></div>
          </li>
        ))}
      </ul>
    </section>
  )
}
