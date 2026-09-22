import type { PageResponse } from './adminTypes.ts'

export type CourseType = 'MANDATORY' | 'OPTIONAL'
export type CourseStatus = 'DRAFT' | 'OPEN' | 'CLOSED'
export type ContentType = 'VIDEO' | 'DOCUMENT' | 'LINK'

export type Course = {
  id: number
  title: string
  description: string | null
  courseType: CourseType
  status: CourseStatus
  startDate: string | null
  endDate: string | null
  passingProgressRate: number
  instructorId: number | null
  instructorName: string | null
  createdAt: string
  updatedAt: string
}

export type CourseSearch = {
  page: number
  size: number
  keyword?: string
  status?: CourseStatus
  type?: CourseType
  instructorId?: number
}

export type CourseCreateRequest = {
  title: string
  description?: string | null
  courseType: CourseType
  startDate?: string | null
  endDate?: string | null
  passingProgressRate: number
  instructorId?: number | null
}

export type CoursePatchRequest = Partial<CourseCreateRequest>
export type CoursePage = PageResponse<Course>

export type CourseContent = {
  id: number
  courseId: number
  title: string
  contentType: ContentType
  contentUrl: string
  durationSeconds: number | null
  sortOrder: number
  required: boolean
  createdAt: string
  updatedAt: string
}

export type CourseContentCreateRequest = {
  title: string
  contentType: ContentType
  contentUrl: string
  durationSeconds?: number | null
  sortOrder: number
  required: boolean
}

export type CourseContentPatchRequest = Partial<Omit<CourseContentCreateRequest, 'sortOrder'>>
