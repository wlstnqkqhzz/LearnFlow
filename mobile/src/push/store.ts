import { isAxiosError } from 'axios';
import type { createPushApi } from './api';
import type { createInstallationStore, Installation, Subscription } from './identity';

export type Permission = 'undetermined' | 'granted' | 'denied';
export type PushNative = {
  platform: Subscription['platform']; blocker: string;
  channel(): Promise<void>; permission(): Promise<Permission>; requestPermission(): Promise<Permission>; token(): Promise<string>;
};
type Phase = 'idle' | 'binding' | 'off' | 'permission' | 'token' | 'registering' | 'on' | 'disabling' | 'binding-error' | 'token-error' | 'registration-error' | 'off-error';
type State = { phase: Phase; enabled: boolean; busy: boolean; permission: Permission; error: string; blocker: string };
const status = (error: unknown) => isAxiosError(error) ? error.response?.status : undefined;

export function createPushStore(api: ReturnType<typeof createPushApi>, identityStore: ReturnType<typeof createInstallationStore>, native: PushNative) {
  const initial = (): State => ({ phase: 'idle', enabled: false, busy: false, permission: 'undetermined', error: '', blocker: native.blocker });
  let state = initial(), member: number | null = null, generation = 0;
  let controller = new AbortController(), identity: Installation | null = null, binding: Subscription | null = null;
  let registeredToken: string | null = null;
  let work: Promise<void> | null = null, cleanup: Promise<void> = Promise.resolve();
  const listeners = new Set<() => void>();
  const publish = (patch: Partial<State>) => { state = { ...state, ...patch }; listeners.forEach(fn => fn()); };
  const valid = (ticket: number) => ticket === generation && member !== null;
  function guard(ticket: number) { if (!valid(ticket)) throw new Error('cancelled'); }
  function run(operation: (ticket: number) => Promise<void>) {
    if (work) return work;
    const ticket = generation;
    const promise = operation(ticket).finally(() => { if (work === promise) work = null; if (valid(ticket)) publish({ busy: false }); });
    work = promise; return promise;
  }
  async function save(ticket: number, value: Subscription) {
    guard(ticket); binding = value;
    const saved = await identityStore.update({ binding: value, memberId: member });
    guard(ticket); identity = saved;
  }
  async function synchronize(ticket: number) {
    publish({ phase: 'binding', busy: true, enabled: false, error: '' });
    await cleanup; guard(ticket);
    const loaded = await identityStore.get(); guard(ticket); identity = loaded;
    let version = identity.binding?.version ?? 0;
    if (identity.binding) {
      try { version = (await api.get(identity, identity.binding.subscriptionId, controller.signal)).version; }
      catch (error) { if (status(error) !== 404) throw error; }
      guard(ticket);
    }
    const result = await api.bind(identity, native.platform, version, controller.signal); guard(ticket);
    await save(ticket, result);
    if (!native.blocker) { const permission = await native.permission(); guard(ticket); publish({ permission }); }
    // Permission alone never enables Push. Only restore the same member's explicit opt-in.
    if (identity!.optedInMember !== member && result.enabled) await disable(ticket);
    publish({ phase: 'off', enabled: false });
    if (result.enabled && identity!.optedInMember === member && !native.blocker) await enable(ticket, false);
  }
  async function disable(ticket: number) {
    guard(ticket); if (!identity || !binding) return;
    publish({ phase: 'disabling', busy: true, error: '' });
    await identityStore.update({ optedInMember: null }); guard(ticket);
    const current = await api.get(identity, binding.subscriptionId, controller.signal); guard(ticket);
    await save(ticket, await api.disable(identity, current, controller.signal)); guard(ticket);
    publish({ enabled: false, phase: 'off' });
  }
  async function enable(ticket: number, explicit: boolean) {
    guard(ticket); if (!identity || !binding) throw new Error('binding');
    if (native.blocker) { publish({ phase: 'off', enabled: false, error: native.blocker }); return; }
    publish({ busy: true, error: '', phase: 'permission', enabled: false });
    await native.channel(); guard(ticket);
    let permission = await native.permission(); guard(ticket);
    if (explicit && permission === 'undetermined') { permission = await native.requestPermission(); guard(ticket); }
    publish({ permission });
    if (permission !== 'granted') {
      if (binding.enabled) await disable(ticket);
      publish({ phase: 'off', enabled: false, error: permission === 'denied' ? '알림 권한이 거부되었습니다. 기기 설정에서 허용할 수 있습니다.' : '' }); return;
    }
    publish({ phase: 'token' });
    const token = await native.token(); guard(ticket);
    if (!/^(ExpoPushToken|ExponentPushToken)\[[A-Za-z0-9_-]+\]$/.test(token)) throw new Error('token');
    publish({ phase: 'registering' });
    const current = await api.get(identity, binding.subscriptionId, controller.signal); guard(ticket);
    const registered = current.enabled && current.version === binding.version && token === registeredToken
      ? current : await api.register(identity, current, token, native.platform, controller.signal);
    guard(ticket); registeredToken = token;
    await save(ticket, registered);
    const opted = await identityStore.update({ optedInMember: member }); guard(ticket); identity = opted;
    publish({ enabled: registered.enabled, phase: registered.enabled ? 'on' : 'off', error: '' });
  }
  function failed(ticket: number, error: unknown) {
    if (!valid(ticket)) return;
    const phase = state.phase === 'binding' ? 'binding-error' : state.phase === 'token' ? 'token-error' : state.phase === 'disabling' ? 'off-error' : 'registration-error';
    const messages = { 'binding-error': '기기 연결에 실패했습니다. 다시 시도해 주세요.', 'token-error': 'Push Token을 발급하지 못했습니다. 연결 상태를 확인해 주세요.', 'off-error': '서버 알림 해제에 실패했습니다. 다시 시도해 주세요.', 'registration-error': '서버 알림 등록을 완료하지 못했습니다. 다시 시도해 주세요.' };
    publish({ phase, error: status(error) === 409 ? '기기 구독 버전이 변경되었거나 Token이 사용 중입니다. 기기 연결을 다시 시도해 주세요.' : messages[phase], enabled: phase === 'off-error' && !!binding?.enabled });
  }
  return {
    snapshot: () => state,
    subscribe(fn: () => void) { listeners.add(fn); return () => { listeners.delete(fn); }; },
    setMember(next: number | null) {
      if (next === member) return work ?? Promise.resolve();
      generation++; controller.abort(); controller = new AbortController(); work = null; binding = null; identity = null; registeredToken = null; member = next; publish(initial());
      if (member === null) return Promise.resolve();
      return run(async ticket => { try { await synchronize(ticket); } catch (error) { failed(ticket, error); } });
    },
    retry() { if (member === null) return Promise.resolve(); return run(async ticket => { try { await synchronize(ticket); } catch (error) { failed(ticket, error); } }); },
    on() { return run(async ticket => { try { if (!binding) await synchronize(ticket); await enable(ticket, true); } catch (error) { failed(ticket, error); } }); },
    off() { return run(async ticket => { try { await disable(ticket); } catch (error) { failed(ticket, error); } }); },
    reconcile() {
      if (member === null || !binding || native.blocker) return Promise.resolve();
      return run(async ticket => {
        try {
          if (state.enabled) await enable(ticket, false);
          else { const permission = await native.permission(); guard(ticket); publish({ permission }); }
        } catch (error) { failed(ticket, error); }
      });
    },
    logout(cleanupApi: ReturnType<typeof createPushApi>) {
      const previousWork = work, oldIdentity = identity, oldBinding = binding, oldMember = member;
      generation++; controller.abort(); controller = new AbortController(); work = null; member = null; binding = null; identity = null; registeredToken = null; publish(initial());
      cleanup = (async () => {
        // This runs separately from auth cleanup; next binding waits for its version reconciliation.
        await identityStore.update({ optedInMember: null });
        // A native permission sheet may remain open indefinitely after logout.
        if (previousWork) await new Promise<void>(resolve => {
          const timer = setTimeout(resolve, 1000);
          void previousWork.then(() => { clearTimeout(timer); resolve(); }, () => { clearTimeout(timer); resolve(); });
        });
        const saved = oldIdentity ?? await identityStore.get();
        const known = oldBinding ?? saved.binding;
        if (!known || oldMember === null) return;
        const current = await cleanupApi.get(saved, known.subscriptionId);
        const disabled = await cleanupApi.disable(saved, current);
        await identityStore.update({ binding: disabled, memberId: oldMember, optedInMember: null });
      })().catch(() => {});
      return cleanup;
    },
  };
}
