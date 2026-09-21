export const enrollmentStatus = {
  ASSIGNED: { label: '배정됨', tone: 'neutral' },
  IN_PROGRESS: { label: '학습 중', tone: 'primary' },
  COMPLETED: { label: '수료', tone: 'success' },
  FAILED: { label: '실패', tone: 'danger' },
  EXPIRED: { label: '만료', tone: 'warning' },
} as const

export type EnrollmentStatus = keyof typeof enrollmentStatus
