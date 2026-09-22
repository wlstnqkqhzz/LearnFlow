import { apiClient } from './client.ts'
import type { AssignmentRule, AssignmentRuleCreateRequest, AssignmentRuleRequest } from './assignmentTypes.ts'

export const assignmentRuleApi = {
  async list(courseId: number, signal?: AbortSignal) { return (await apiClient.get<AssignmentRule[]>(`/courses/${courseId}/assignment-rules`, { signal })).data },
  async create(courseId: number, data: AssignmentRuleCreateRequest) { return (await apiClient.post<AssignmentRule>(`/courses/${courseId}/assignment-rules`, data)).data },
  async update(courseId: number, ruleId: number, data: AssignmentRuleRequest) { return (await apiClient.patch<AssignmentRule>(`/courses/${courseId}/assignment-rules/${ruleId}`, data)).data },
  async status(courseId: number, ruleId: number, active: boolean) { return (await apiClient.patch<AssignmentRule>(`/courses/${courseId}/assignment-rules/${ruleId}/status`, { active })).data },
}
