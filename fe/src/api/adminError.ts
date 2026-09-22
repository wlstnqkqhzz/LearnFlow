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
  COURSE_NOT_FOUND: '교육과정을 찾을 수 없습니다.', COURSE_CONTENT_NOT_FOUND: '해당 교육과정의 콘텐츠를 찾을 수 없습니다.',
  INVALID_COURSE_INSTRUCTOR: 'INSTRUCTOR 역할이 있는 회원만 강사로 지정할 수 있습니다.',
  INVALID_COURSE_PERIOD: '운영 시작일은 종료일보다 늦을 수 없습니다.', COURSE_DATES_REQUIRED: '과정 오픈에는 운영 시작일과 종료일이 필요합니다.',
  INVALID_COURSE_STATUS_TRANSITION: '현재 상태에서는 요청한 과정 상태로 변경할 수 없습니다.',
  DUPLICATE_CONTENT_SORT_ORDER: '이미 사용 중인 콘텐츠 순서입니다. 목록을 새로고침해 주세요.',
  INVALID_CONTENT_ORDER: '현재 과정의 모든 콘텐츠를 중복 없이 포함해야 합니다.',
  CONTENT_ORDER_LIMIT_EXCEEDED: '콘텐츠 순서를 저장할 수 없습니다. 기존 순서를 정리한 뒤 다시 시도해 주세요.',
  DATA_CONFLICT: '다른 데이터에서 사용 중이므로 삭제하거나 변경할 수 없습니다.',
  ASSIGNMENT_RULE_NOT_FOUND: '배정 규칙을 찾을 수 없습니다.', ENROLLMENT_NOT_FOUND: '수강 내역을 찾을 수 없습니다.',
  INVALID_ASSIGNMENT_RULE_TARGET: '배정 유형에 맞는 대상 조건을 입력해 주세요.', INVALID_NEW_EMPLOYEE_DAYS: '신입사원 대상 기간은 1~32767일이어야 합니다.',
  DUPLICATE_ENROLLMENT: '이미 해당 교육과정에 배정된 직원입니다.', COURSE_NOT_OPEN_FOR_ASSIGNMENT: '운영 중인 교육과정에만 직원을 배정할 수 있습니다.',
  RESIGNED_MEMBER_ASSIGNMENT: '퇴사한 직원은 교육과정에 배정할 수 없습니다.',
  EXAM_NOT_FOUND: '해당 과정에 등록된 시험이 없습니다.', DUPLICATE_EXAM: '과정에는 시험을 하나만 등록할 수 있습니다.',
  QUESTION_NOT_FOUND: '문항을 찾을 수 없습니다.', QUESTION_CHOICE_NOT_FOUND: '선택지를 찾을 수 없습니다.',
  INVALID_EXAM_CONFIGURATION: '문항과 정답 구성을 완성해야 응시할 수 있습니다.',
  INVALID_QUESTION_CONFIGURATION: '문항 유형에 맞는 선택지와 정답을 구성해 주세요.',
  DUPLICATE_QUESTION_SORT_ORDER: '이미 사용 중인 문항 순서입니다.', DUPLICATE_CHOICE_SORT_ORDER: '이미 사용 중인 선택지 순서입니다.',
  INVALID_QUESTION_ORDER: '현재 시험의 모든 문항을 중복 없이 포함해야 합니다.', INVALID_CHOICE_ORDER: '현재 문항의 모든 선택지를 중복 없이 포함해야 합니다.',
  EXAM_HISTORY_DELETE_CONFLICT: '응시 이력이 있는 문항과 선택지는 삭제할 수 없습니다.',
  EXAM_CONFIGURATION_LOCKED: '이미 시험 응시가 시작되어 시험 구성을 변경할 수 없습니다.',
}
export function adminErrorCode(error: unknown) {
  if (!axios.isAxiosError(error)) return undefined
  const code: unknown = error.response?.data?.code
  return typeof code === 'string' ? code : undefined
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
