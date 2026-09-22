import type { CourseStatus, CourseType, ContentType } from '../../api/courseTypes.ts'
import { contentTypes, courseStatuses, courseTypes } from '../../pages/admin/courseUtils.ts'

export function CourseStatusBadge({ status }: { status: CourseStatus }) {
  const value = courseStatuses[status]
  return <span className={`status-badge tone-${value.tone}`}>{value.label}</span>
}

export function CourseTypeBadge({ type }: { type: CourseType }) {
  return <span className={`status-badge tone-${type === 'MANDATORY' ? 'primary' : 'neutral'}`}>{courseTypes[type]}</span>
}

export function ContentTypeBadge({ type }: { type: ContentType }) {
  return <span className="status-badge tone-primary">{contentTypes[type]}</span>
}
