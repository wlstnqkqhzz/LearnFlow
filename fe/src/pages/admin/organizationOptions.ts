import { departmentApi } from '../../api/departmentApi.ts'
import { jobPositionApi } from '../../api/jobPositionApi.ts'

export async function loadOrganizationOptions(signal: AbortSignal) {
  const [departments, jobs] = await Promise.all([departmentApi.list(false, signal), jobPositionApi.list(false, signal)])
  return { departments, jobs }
}
