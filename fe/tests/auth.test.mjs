import assert from 'node:assert/strict'
import { test } from 'node:test'
import { AxiosError } from 'axios'
import { authStorage } from '../src/auth/authStorage.ts'
import { authSession } from '../src/auth/authSession.ts'
import { initializeAuth, login, logout } from '../src/auth/authActions.ts'
import { apiClient, publicClient, refreshAccessToken, onForbidden } from '../src/api/client.ts'
import { homePath, readAccessToken } from '../src/auth/tokenUtils.ts'
import { apiErrorMessage } from '../src/api/apiError.ts'

// Node 내장 테스트만 사용한다. 가짜 토큰과 Axios adapter는 이 파일에서만 사용한다.
const storage = new Map()
Object.defineProperty(globalThis, 'sessionStorage', { value: {
  getItem: key => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, value),
  removeItem: key => storage.delete(key),
}, configurable: true })

function token(roles = ['EMPLOYEE', 'ADMIN'], expiry = 3600) {
  const payload = { tokenType: 'ACCESS', memberId: 1, email: '테스트@example.com', roles, exp: Math.floor(Date.now() / 1000) + expiry }
  return `test.${Buffer.from(JSON.stringify(payload)).toString('base64url')}.test`
}
function pair(refresh = 'test-refresh-1', access = token()) {
  return { accessToken: access, refreshToken: refresh, tokenType: 'Bearer', accessTokenExpiresInSeconds: 3600, refreshTokenExpiresInSeconds: 7200 }
}
function response(config, data = {}, status = 200) { return { config, data, status, statusText: '', headers: {} } }
function reject(config, status) { throw new AxiosError('Test error', undefined, config, undefined, response(config, {}, status)) }
function deferred() { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
function seed(value = pair()) { authSession.clear(); authSession.accept(value, authSession.getGeneration()) }

await test('초기 인증 복구는 중복 호출해도 재발급 한 번이며 최신 역할을 반영한다', async () => {
  seed()
  let calls = 0
  publicClient.defaults.adapter = async config => { calls++; return response(config, pair('restored', token(['EMPLOYEE']))) }
  await Promise.all([initializeAuth(), initializeAuth()])
  assert.equal(calls, 1)
  assert.deepEqual(authSession.getSnapshot().user.roles, ['EMPLOYEE'])
  assert.equal(authSession.getSnapshot().isInitializing, false)
})

await test('로그인은 Bearer 없이 실제 DTO를 전송하고 두 토큰을 저장한다', async () => {
  publicClient.defaults.adapter = async config => {
    assert.equal(config.url, '/auth/login')
    assert.equal(config.headers.get('Authorization'), undefined)
    assert.deepEqual(JSON.parse(config.data), { email: 'user@example.com', password: ' test-password ' })
    return response(config, pair())
  }
  const user = await login({ email: ' user@example.com ', password: ' test-password ' })
  assert.equal(user.memberId, 1)
  assert.equal(authStorage.read().refreshToken, 'test-refresh-1')
  assert.equal([...storage.values()].some(value => value.includes('test-password')), false)
})

await test('로그인 401은 재발급하지 않고 인증 상태를 남기지 않는다', async () => {
  let calls = 0
  publicClient.defaults.adapter = async config => { calls++; return reject(config, 401) }
  await assert.rejects(login({ email: 'user@example.com', password: 'test-password' }))
  assert.equal(calls, 1)
  assert.equal(authSession.getSnapshot().user, null)
  assert.equal(authStorage.read(), null)
})

await test('동시 401 세 건은 refresh 한 번을 공유하고 각각 한 번 재시도한다', async () => {
  seed()
  let refreshes = 0
  const waiting = deferred()
  const attempts = new Map()
  publicClient.defaults.adapter = async config => {
    refreshes++
    assert.equal(config.headers.get('Authorization'), undefined)
    assert.equal(JSON.parse(config.data).refreshToken, 'test-refresh-1')
    await waiting.promise
    return response(config, pair('test-refresh-2'))
  }
  apiClient.defaults.adapter = async config => {
    attempts.set(config.url, (attempts.get(config.url) ?? 0) + 1)
    if (!config._retry) return reject(config, 401)
    assert.equal(config.headers.get('Authorization'), `Bearer ${authSession.getTokens().accessToken}`)
    return response(config)
  }
  const all = Promise.all(['/one', '/two', '/three'].map(url => apiClient.get(url)))
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(refreshes, 1)
  waiting.resolve()
  await all
  assert.deepEqual([...attempts.values()], [2, 2, 2])
  assert.equal(authStorage.read().refreshToken, 'test-refresh-2')
})

await test('늦게 도착한 401은 Access 문자열이 같아도 재발급을 반복하지 않는다', async () => {
  const access = token()
  seed(pair('old', access))
  let refreshes = 0
  const late = deferred()
  publicClient.defaults.adapter = async config => { refreshes++; return response(config, pair('new', access)) }
  apiClient.defaults.adapter = async config => {
    if (!config._retry) { if (config.url === '/late') await late.promise; return reject(config, 401) }
    return response(config)
  }
  const lateRequest = apiClient.get('/late')
  await apiClient.get('/early')
  late.resolve()
  await lateRequest
  assert.equal(refreshes, 1)
})

await test('재발급 401은 루프 없이 저장소와 사용자를 정리한다', async () => {
  seed()
  let calls = 0
  publicClient.defaults.adapter = async config => { calls++; return reject(config, 401) }
  apiClient.defaults.adapter = async config => reject(config, 401)
  await assert.rejects(apiClient.get('/private'))
  assert.equal(calls, 1)
  assert.equal(authSession.getSnapshot().user, null)
  assert.equal(authStorage.read(), null)
})

await test('재시도도 401이면 추가 refresh 없이 인증을 정리한다', async () => {
  seed()
  let calls = 0
  publicClient.defaults.adapter = async config => { calls++; return response(config, pair('rotated')) }
  apiClient.defaults.adapter = async config => reject(config, 401)
  await assert.rejects(apiClient.get('/private'))
  assert.equal(calls, 1)
  assert.equal(authSession.getSnapshot().user, null)
})

await test('403은 refresh 없이 권한 없음 이벤트만 알리고 인증은 유지한다', async () => {
  seed()
  let refreshes = 0
  let forbidden = 0
  const unsubscribe = onForbidden(() => { forbidden++ })
  publicClient.defaults.adapter = async config => { refreshes++; return response(config, pair()) }
  apiClient.defaults.adapter = async config => reject(config, 403)
  await assert.rejects(apiClient.get('/denied'))
  unsubscribe()
  assert.equal(refreshes, 0)
  assert.equal(forbidden, 1)
  assert.ok(authSession.getSnapshot().user)
})

await test('로그아웃은 Bearer와 빈 본문으로 호출하고 토큰을 삭제한다', async () => {
  seed()
  apiClient.defaults.adapter = async config => {
    assert.equal(config.url, '/auth/logout')
    assert.ok(config.headers.get('Authorization'))
    assert.equal(config.data, undefined)
    return response(config, undefined, 204)
  }
  await logout()
  assert.equal(authStorage.read(), null)
  assert.equal(authSession.getSnapshot().user, null)
})

await test('네트워크 오류에도 로컬 로그아웃은 완료한다', async () => {
  seed()
  apiClient.defaults.adapter = async config => { throw new AxiosError('Network error', 'ERR_NETWORK', config) }
  await logout()
  assert.equal(authStorage.read(), null)
  assert.equal(authSession.getSnapshot().user, null)
})

await test('로그아웃 후 도착하는 refresh 성공 응답은 인증을 복원하지 않는다', async () => {
  seed()
  const waiting = deferred()
  publicClient.defaults.adapter = async config => { await waiting.promise; return response(config, pair('late-refresh')) }
  apiClient.defaults.adapter = async config => response(config, undefined, 204)
  const refreshing = refreshAccessToken()
  const rejected = assert.rejects(refreshing)
  await logout()
  waiting.resolve()
  await rejected
  assert.equal(authStorage.read(), null)
})

await test('이전 세션의 늦은 401은 새 로그인 세션을 삭제하지 않는다', async () => {
  seed()
  const waiting = deferred()
  apiClient.defaults.adapter = async config => { await waiting.promise; return reject(config, 401) }
  const oldRequest = apiClient.get('/old')
  const rejected = assert.rejects(oldRequest)
  await new Promise(resolve => setImmediate(resolve))
  seed(pair('new-session'))
  waiting.resolve()
  await rejected
  assert.equal(authSession.getTokens().refreshToken, 'new-session')
})

await test('만료된 refresh는 서버 요청 없이 인증을 제거한다', async () => {
  seed()
  authStorage.write({ ...authStorage.read(), refreshExpiresAt: 0 })
  authSession.load()
  let calls = 0
  publicClient.defaults.adapter = async config => { calls++; return response(config, pair()) }
  await assert.rejects(refreshAccessToken())
  assert.equal(calls, 0)
  assert.equal(authStorage.read(), null)
})

await test('JWT UTF-8 처리, 복수 역할 우선순위 및 잘못된 payload 처리', () => {
  const { user } = readAccessToken(token())
  assert.equal(user.email, '테스트@example.com')
  assert.equal(homePath(user), '/admin/dashboard')
  assert.equal(homePath({ ...user, roles: ['EMPLOYEE', 'INSTRUCTOR'] }), '/employee')
  assert.equal(homePath({ ...user, roles: ['INSTRUCTOR'] }), '/instructor')
  assert.throws(() => readAccessToken('invalid'))
  assert.throws(() => readAccessToken(token(['UNKNOWN'])))
  authSession.clear()
  assert.throws(() => authSession.accept(pair('refresh', token(['EMPLOYEE'], -1)), authSession.getGeneration()))
  assert.equal(authStorage.read(), null)
})

await test('서버 오류 원문과 비밀번호는 오류 UI로 전달하지 않는다', () => {
  const error = new AxiosError('password=private', undefined, undefined, undefined, { status: 500, data: { message: 'SQL secret' } })
  assert.equal(apiErrorMessage(error), '서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.')
})

await test('Access 만료 상태의 로그아웃은 재발급 후 한 번 재시도한다', async () => {
  seed()
  let refreshes = 0
  let logouts = 0
  publicClient.defaults.adapter = async config => { refreshes++; return response(config, pair('logout-rotation')) }
  apiClient.defaults.adapter = async config => {
    logouts++
    if (!config._retry) return reject(config, 401)
    return response(config, undefined, 204)
  }
  await logout()
  assert.equal(refreshes, 1)
  assert.equal(logouts, 2)
  assert.equal(authStorage.read(), null)
})

await test('손상된 저장소 값은 예외 없이 제거한다', () => {
  storage.set('learnflow.auth', 'broken-json')
  assert.equal(authStorage.read(), null)
  assert.equal(storage.size, 0)
})

await test('재발급 네트워크 오류도 인증 상태를 초기화한다', async () => {
  seed()
  publicClient.defaults.adapter = async config => { throw new AxiosError('Network error', 'ERR_NETWORK', config) }
  await assert.rejects(refreshAccessToken())
  assert.equal(authSession.getSnapshot().user, null)
  assert.equal(authStorage.read(), null)
})
