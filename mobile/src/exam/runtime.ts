import { apiClient } from '@/auth/runtime';
import { createExamApi } from './api';
export const examApi = createExamApi(apiClient);
