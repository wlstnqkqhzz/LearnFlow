import { apiClient } from './client.ts'
import axios from 'axios'
import type {
  AdminQuestion, AdminQuestionChoice, ChoiceCreateRequest, ChoicePatchRequest,
  Exam, ExamCreateRequest, ExamPatchRequest, QuestionCreateRequest, QuestionPatchRequest,
} from './examTypes.ts'

export const examApi = {
  async get(courseId: number, signal?: AbortSignal) {
    return (await apiClient.get<Exam>(`/courses/${courseId}/exam`, { signal })).data
  },
  async getOptional(courseId: number, signal?: AbortSignal) {
    try { return (await apiClient.get<Exam>(`/courses/${courseId}/exam`, { signal })).data }
    catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404 && error.response.data?.code === 'EXAM_NOT_FOUND') return null
      throw error
    }
  },
  async create(courseId: number, data: ExamCreateRequest) {
    return (await apiClient.post<Exam>(`/courses/${courseId}/exam`, data)).data
  },
  async update(courseId: number, data: ExamPatchRequest) {
    return (await apiClient.patch<Exam>(`/courses/${courseId}/exam`, data)).data
  },
  async validate(courseId: number) {
    return (await apiClient.get<Exam>(`/courses/${courseId}/exam/validation`)).data
  },
}

export const questionApi = {
  async list(courseId: number, signal?: AbortSignal) {
    return (await apiClient.get<AdminQuestion[]>(`/courses/${courseId}/exam/questions`, { signal })).data
  },
  async create(courseId: number, data: QuestionCreateRequest) {
    return (await apiClient.post<AdminQuestion>(`/courses/${courseId}/exam/questions`, data)).data
  },
  async update(courseId: number, questionId: number, data: QuestionPatchRequest) {
    return (await apiClient.patch<AdminQuestion>(`/courses/${courseId}/exam/questions/${questionId}`, data)).data
  },
  async delete(courseId: number, questionId: number) {
    await apiClient.delete(`/courses/${courseId}/exam/questions/${questionId}`)
  },
  async reorder(courseId: number, questionIds: number[]) {
    return (await apiClient.patch<AdminQuestion[]>(`/courses/${courseId}/exam/questions/order`, { questionIds })).data
  },
  async createChoice(courseId: number, questionId: number, data: ChoiceCreateRequest) {
    return (await apiClient.post<AdminQuestionChoice>(`/courses/${courseId}/exam/questions/${questionId}/choices`, data)).data
  },
  async updateChoice(courseId: number, questionId: number, choiceId: number, data: ChoicePatchRequest) {
    return (await apiClient.patch<AdminQuestionChoice>(`/courses/${courseId}/exam/questions/${questionId}/choices/${choiceId}`, data)).data
  },
  async deleteChoice(courseId: number, questionId: number, choiceId: number) {
    await apiClient.delete(`/courses/${courseId}/exam/questions/${questionId}/choices/${choiceId}`)
  },
  async reorderChoices(courseId: number, questionId: number, choiceIds: number[]) {
    return (await apiClient.patch<AdminQuestionChoice[]>(`/courses/${courseId}/exam/questions/${questionId}/choices/order`, { choiceIds })).data
  },
}
