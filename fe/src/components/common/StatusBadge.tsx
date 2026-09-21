import { enrollmentStatus, type EnrollmentStatus } from './enrollmentStatus'

export function StatusBadge({ status }: { status: EnrollmentStatus }) {
  const { label, tone } = enrollmentStatus[status]
  return <span className={`status-badge tone-${tone}`}>{label}</span>
}
