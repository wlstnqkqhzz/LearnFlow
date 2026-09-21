import { activeCourses } from '../../data/dashboardMockData'
import { Icon } from '../common/Icon'
import { ProgressBar } from '../common/Progress'

export function ActiveCourseList() {
  return (
    <section aria-labelledby="active-courses-title">
      <div className="section-heading">
        <div><h2 id="active-courses-title">운영 중인 교육</h2><p className="section-description">현재 배정되어 진행되고 있는 과정</p></div>
        <button type="button" className="text-button" disabled title="교육과정 목록 준비 중">전체 보기 <Icon name="arrow" /></button>
      </div>
      <ul className="course-list surface">
        {activeCourses.map((course) => (
          <li className="course-row" key={course.id}>
            <span className="course-icon"><Icon name="book" /></span>
            <div className="course-copy"><h3>{course.title}</h3><p>{course.type}<span aria-hidden="true"> · </span>수강 {course.learners}명</p></div>
            <div className="course-progress"><ProgressBar value={course.progress} label={`${course.title} 평균 진도`} /><span>{course.progress}%</span></div>
          </li>
        ))}
      </ul>
    </section>
  )
}
