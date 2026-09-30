import type { PageResponse } from './adminTypes.ts'

export type RetrainingPolicyRequest = {
  sourceCourseId: number
  baseTitle: string
  enabled: boolean
  autoCreate: boolean
  autoOpen: boolean
  intervalMonths: number
  firstStartDate: string
  durationDays: number
  generationLeadDays: number
}

export type RetrainingPolicy = RetrainingPolicyRequest & {
  id: number
  nextOccurrenceNumber: number
  nextGenerationDate: string
  createdAt: string
  updatedAt: string
}
export type RetrainingPolicyPage = PageResponse<RetrainingPolicy>
