import { create, CanceledError, isAxiosError, type InternalAxiosRequestConfig } from 'axios';

export type Tokens = { accessToken: string; refreshToken: string; tokenType: string; accessTokenExpiresInSeconds: number; refreshTokenExpiresInSeconds: number };
export type User = { memberId: number; email: string; roles: string[] };
export type AuthState = { status: 'loading' | 'anonymous' | 'authenticated'; user: User | null; message: string };
export type TokenStorage = { read(): Promise<string | null>; write(value: string): Promise<void>; remove(): Promise<void> };
type Request = InternalAxiosRequestConfig & { authEpoch?: number; authRevision?: number; authRetried?: boolean };

export function apiAddress(value: string | undefined, native = true) {
  if (!value?.trim()) throw new Error('EXPO_PUBLIC_API_BASE_URL을 설정해 주세요.');
  let url: URL;
  try { url = new URL(value.trim()); } catch { throw new Error('API 주소 형식을 확인해 주세요.'); }
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash || (native && ['localhost', '127.0.0.1', '[::1]', '0.0.0.0'].includes(url.hostname))) throw new Error('기기에서 접근 가능한 http/https API 주소를 입력해 주세요.');
  return url.toString().replace(/\/$/, '');
}
export function readUser(token: string): User {
  const parts = token.split('.');
  if (parts.length !== 3 || parts.some(p => !p)) throw new Error('올바른 인증 응답이 아닙니다.');
  const encoded = parts[1].replace(/-/g, '+').replace(/_/g, '/');
  const text = decodeURIComponent(Array.from(atob(encoded.padEnd(Math.ceil(encoded.length / 4) * 4, '=')), c => `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`).join(''));
  const p = JSON.parse(text);
  if (p.tokenType !== 'ACCESS' || !Number.isSafeInteger(p.memberId) || p.memberId <= 0 || typeof p.email !== 'string' || !p.email.trim() || !Array.isArray(p.roles) || !p.roles.length || !p.roles.every((r: unknown) => typeof r === 'string' && ['EMPLOYEE', 'ADMIN', 'INSTRUCTOR'].includes(r)) || !Number.isFinite(p.exp) || p.exp * 1000 <= Date.now()) throw new Error('올바른 인증 응답이 아닙니다.');
  // UI routing only. Token signature and authorization are verified by the Backend.
  return { memberId: p.memberId, email: p.email, roles: p.roles };
}
export function authMessage(error: unknown) {
  if (isAxiosError(error)) {
    if (error.response?.status === 401) return '이메일과 비밀번호를 확인해 주세요. 로그인할 수 없는 계정일 수 있습니다.';
    if (error.response?.status === 403) return '이 작업에 접근할 권한이 없습니다.';
    if (!error.response) return '서버에 연결할 수 없습니다. 네트워크와 API 주소를 확인해 주세요.';
    return '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';
  }
  return error instanceof Error && ['직원 역할이 없는 계정입니다. 관리자 웹을 이용해 주세요.', '인증 저장소를 사용할 수 없습니다. 다시 시도해 주세요.'].includes(error.message) ? error.message : '인증을 완료하지 못했습니다. 다시 로그인해 주세요.';
}
export function loginValidation(email: string, password: string) {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) || email.trim().length > 255) return '올바른 이메일을 입력해 주세요.';
  if (!password.trim() || password.length > 128) return '비밀번호를 입력해 주세요. 최대 128자입니다.';
  return '';
}

export function createAuthSession(storage: TokenStorage, baseURL: string) {
  const publicClient = create({ baseURL, timeout: 15000 });
  const client = create({ baseURL, timeout: 15000 });
  let state: AuthState = { status: 'loading', user: null, message: '' };
  let tokens: Tokens | null = null;
  let epoch = 0, revision = 0;
  let writes: Promise<unknown> = Promise.resolve();
  let flight: { epoch: number; promise: Promise<void> } | null = null;
  let boot: Promise<void> | null = null;
  let signingIn = false;
  const listeners = new Set<() => void>();
  const publish = (next: AuthState) => { state = next; listeners.forEach(fn => fn()); };
  const serialize = <T,>(action: () => Promise<T>): Promise<T> => { const result = writes.then(action); writes = result.catch(() => {}); return result; };
  const cancelled = () => new CanceledError('인증 상태가 변경되었습니다.');
  async function clear(message = '') {
    const ticket = ++epoch; tokens = null; revision++;
    publish({ status: 'anonymous', user: null, message });
    try { await serialize(async () => { try { await storage.remove(); } catch { await storage.write(''); } }); }
    catch { if (epoch === ticket) publish({ status: 'anonymous', user: null, message: '인증 저장소를 사용할 수 없습니다. 다시 시도해 주세요.' }); throw new Error('인증 저장소를 사용할 수 없습니다. 다시 시도해 주세요.'); }
  }
  async function accept(data: Tokens, ticket: number) {
    if (epoch !== ticket) throw cancelled();
    if (!data || typeof data.accessToken !== 'string' || typeof data.refreshToken !== 'string' || !data.refreshToken || data.tokenType !== 'Bearer' || !(data.accessTokenExpiresInSeconds > 0) || !(data.refreshTokenExpiresInSeconds > 0)) throw new Error('Invalid authentication response');
    const user = readUser(data.accessToken);
    if (!user.roles.includes('EMPLOYEE')) throw new Error('직원 역할이 없는 계정입니다. 관리자 웹을 이용해 주세요.');
    await serialize(async () => {
      if (epoch !== ticket) throw cancelled();
      try { await storage.write(JSON.stringify(data)); } catch { throw new Error('인증 저장소를 사용할 수 없습니다. 다시 시도해 주세요.'); }
    });
    if (epoch !== ticket) throw cancelled();
    tokens = data; revision++;
    publish({ status: 'authenticated', user, message: '' });
  }
  function refresh() {
    const ticket = epoch;
    if (flight?.epoch === ticket) return flight.promise;
    const promise = (async () => {
      try {
        if (!tokens?.refreshToken) throw new Error('No session');
        const response = await publicClient.post<Tokens>('/auth/refresh', { refreshToken: tokens.refreshToken });
        await accept(response.data, ticket);
      } catch (error) {
        if (epoch === ticket) await clear('세션이 만료되었거나 연결이 끊겼습니다. 다시 로그인해 주세요.');
        throw error;
      }
    })();
    const current = { epoch: ticket, promise }; flight = current;
    void promise.then(() => { if (flight === current) flight = null; }, () => { if (flight === current) flight = null; });
    return promise;
  }
  client.interceptors.request.use(config => {
    const request = config as Request;
    if (request.authEpoch !== undefined && request.authEpoch !== epoch) throw cancelled();
    if (!tokens || state.status !== 'authenticated') throw cancelled();
    request.authEpoch = epoch; request.authRevision = revision;
    request.headers.set('Authorization', `Bearer ${tokens.accessToken}`);
    return request;
  });
  client.interceptors.response.use(response => {
    if ((response.config as Request).authEpoch !== epoch) throw cancelled();
    return response;
  }, async error => {
    const request = error.config as Request | undefined;
    if (!request || request.authEpoch !== epoch) throw cancelled();
    if (error.response?.status !== 401) throw error;
    if (request.authRetried) { await clear('세션이 만료되었습니다. 다시 로그인해 주세요.'); throw error; }
    request.authRetried = true;
    if (request.authRevision === revision) await refresh();
    if (request.authEpoch !== epoch) throw cancelled();
    return client(request);
  });
  return {
    client, publicClient,
    getSnapshot: () => state,
    subscribe: (fn: () => void) => { listeners.add(fn); return () => { listeners.delete(fn); }; },
    restore() {
      if (boot) return boot;
      const ticket = epoch;
      boot = (async () => {
        try {
          const raw = await serialize(() => storage.read());
          if (epoch !== ticket) return;
          if (!raw) { publish({ status: 'anonymous', user: null, message: '' }); return; }
          const saved = JSON.parse(raw);
          if (typeof saved.refreshToken !== 'string' || !saved.refreshToken) throw new Error('Invalid storage');
          tokens = saved;
          await refresh();
        } catch { if (epoch === ticket) await clear('인증을 복구하지 못했습니다. 다시 로그인해 주세요.').catch(() => {}); }
      })();
      return boot;
    },
    async login(email: string, password: string) {
      if (signingIn) return;
      signingIn = true;
      let ticket = epoch;
      try {
        const invalid = loginValidation(email, password); if (invalid) throw new Error(invalid);
        const cleaning = clear(); ticket = epoch;
        await cleaning;
        if (ticket !== epoch) throw cancelled();
        const { data } = await publicClient.post<Tokens>('/auth/login', { email: email.trim().toLowerCase(), password });
        await accept(data, ticket);
      } catch (error) {
        if (epoch === ticket) await clear(authMessage(error)).catch(() => {});
        throw error;
      } finally { signingIn = false; }
    },
    async logout() {
      const access = tokens?.accessToken;
      const cleaning = clear();
      // Invalidate local state immediately; a server outage must not retain the session.
      const remote = access ? publicClient.post('/auth/logout', null, { headers: { Authorization: `Bearer ${access}` } }).catch(() => {}) : Promise.resolve();
      await cleaning.catch(() => {}); await remote;
    },
  };
}
