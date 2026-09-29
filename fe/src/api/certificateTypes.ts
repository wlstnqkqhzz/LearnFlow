// Backend CertificateResponse. LocalDateTime 문자열은 UTC 기준이다.
export interface Certificate {
  enrollmentId: number
  certificateNumber: string
  memberName: string
  courseTitle: string
  completedAt: string
  issuedAt: string
}
