import axios from 'axios'

// 서버의 임의 message/stack trace/요청 config는 UI나 로그에 전달하지 않는다.
export function apiErrorMessage(error: unknown, login = false): string {
  if (!axios.isAxiosError(error)) return '요청을 완료하지 못했습니다. 다시 시도해 주세요.'
  if (!error.response) return '서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.'
  switch (error.response.status) {
    case 400: return '입력한 정보를 확인해 주세요.'
    case 401: return login ? '이메일 또는 비밀번호를 확인해 주세요. 로그인할 수 없는 계정일 수 있습니다.' : '인증이 만료되었습니다. 다시 로그인해 주세요.'
    case 403: return '현재 계정으로 접근할 수 없습니다.'
    case 429: return '요청이 많습니다. 잠시 후 다시 시도해 주세요.'
    default: return error.response.status >= 500 ? '서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.' : '요청을 처리할 수 없습니다.'
  }
}
