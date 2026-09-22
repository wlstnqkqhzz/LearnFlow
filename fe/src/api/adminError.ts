import axios from 'axios'
import { apiErrorMessage } from './apiError.ts'

const messages: Record<string, string> = {
  DEPARTMENT_NOT_FOUND: '부서를 찾을 수 없습니다. 목록을 다시 확인해 주세요.',
  JOB_POSITION_NOT_FOUND: '직무를 찾을 수 없습니다. 목록을 다시 확인해 주세요.',
  MEMBER_NOT_FOUND: '회원을 찾을 수 없습니다.',
  DUPLICATE_DEPARTMENT_CODE: '이미 사용 중인 부서 코드입니다.',
  DUPLICATE_JOB_POSITION_CODE: '이미 사용 중인 직무 코드입니다.',
  DUPLICATE_EMPLOYEE_NUMBER: '이미 사용 중인 사번입니다.', DUPLICATE_EMAIL: '이미 사용 중인 이메일입니다.',
  INACTIVE_DEPARTMENT: '비활성 부서는 새로 지정할 수 없습니다.', INACTIVE_JOB_POSITION: '비활성 직무는 새로 지정할 수 없습니다.',
  SELF_PARENT_DEPARTMENT: '자기 자신을 상위 부서로 지정할 수 없습니다.', DEPARTMENT_CYCLE: '자신의 하위 부서를 상위 부서로 지정할 수 없습니다.',
  INVALID_MEMBER_STATUS_TRANSITION: '현재 상태에서는 변경할 수 없습니다. 최신 정보를 확인해 주세요.',
  RESIGNED_MEMBER_UPDATE: '퇴사자의 기본 정보와 조직 정보는 수정할 수 없습니다.',
  REQUIRED_EMPLOYEE_ROLE: '직원 역할은 제거할 수 없습니다.', CONCURRENT_MODIFICATION: '다른 변경과 충돌했습니다. 다시 조회한 뒤 시도해 주세요.',
}
export function adminErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const code: unknown = error.response?.data?.code
    if (typeof code === 'string' && Object.hasOwn(messages, code)) return messages[code]
    if (error.response?.status === 404) return '요청한 데이터를 찾을 수 없습니다.'
    if (error.response?.status === 409) return '기존 데이터와 충돌합니다. 최신 정보를 확인해 주세요.'
  }
  return apiErrorMessage(error)
}
