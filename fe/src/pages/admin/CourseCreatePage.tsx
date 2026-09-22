import { Link, useNavigate } from 'react-router-dom'
import { courseApi } from '../../api/courseApi.ts'
import { PageHeader } from '../../components/admin/AdminUI.tsx'
import { useAction } from '../../hooks/useAction.ts'
import { CourseForm } from './CourseForm.tsx'

export function CourseCreatePage() {
  const action = useAction()
  const navigate = useNavigate()
  return <div className="management-page">
    <PageHeader title="교육과정 만들기" description="새 과정은 초안 상태로 생성됩니다."><Link className="admin-button" to="/admin/courses">목록으로</Link></PageHeader>
    <section className="surface detail-section course-editor">
      <CourseForm pending={action.pending} actionError={action.error} submitLabel="교육과정 만들기" onSubmit={async request => {
        await action.run(async () => {
          const created = await courseApi.create(request)
          navigate(`/admin/courses/${created.id}`, { replace: true, state: { created: true } })
        }, '교육과정이 등록되었습니다.')
      }} />
    </section>
  </div>
}
