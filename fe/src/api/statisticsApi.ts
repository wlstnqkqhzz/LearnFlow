import { apiClient } from './client.ts'
import type { StatisticsCourses, StatisticsDepartments, StatisticsFilter, StatisticsOverview, StatisticsTrends } from './statisticsTypes.ts'

export const statisticsApi = {
  async overview(filter: StatisticsFilter, signal?: AbortSignal) {
    return (await apiClient.get<StatisticsOverview>('/admin/statistics/overview', { params: filter, signal })).data
  },
  async trends(filter: StatisticsFilter, signal?: AbortSignal) {
    return (await apiClient.get<StatisticsTrends>('/admin/statistics/trends', { params: filter, signal })).data
  },
  async courses(filter: StatisticsFilter, page: number, signal?: AbortSignal) {
    return (await apiClient.get<StatisticsCourses>('/admin/statistics/courses', { params: { ...filter, page, size: 20 }, signal })).data
  },
  async departments(filter: StatisticsFilter, page: number, signal?: AbortSignal) {
    return (await apiClient.get<StatisticsDepartments>('/admin/statistics/departments', { params: { ...filter, page, size: 20 }, signal })).data
  },
}
