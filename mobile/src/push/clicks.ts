import { notificationId } from '../notifications/resolve.ts';

export type PushResponse = { key: string; value: unknown };
export type ClickHandler = (id: number) => Promise<{ destination: number | null; retryable: boolean; busy?: boolean }>;
export function createPushClicks(clear: () => Promise<void>) {
  let member: number | null = null, ready = false, generation = 0;
  let pending: { key: string; id: number } | null = null;
  let working = false, error = '', handler: ClickHandler | null = null;
  let navigate: ((id: number | null) => void) | null = null;
  const completed = new Set<string>(), listeners = new Set<() => void>();
  let state = { working, error };
  const publish = () => { state = { working, error }; listeners.forEach(fn => fn()); };
  function forget(key: string) { completed.add(key); if (completed.size > 100) completed.delete(completed.values().next().value!); }
  async function drain() {
    if (working || !ready || member === null || !pending || !handler || !navigate) return;
    const job = pending, ticket = generation, action = handler, move = navigate;
    working = true; error = ''; publish();
    try {
      const result = await action(job.id);
      if (ticket !== generation || pending !== job) return;
      if (result.busy) { error = '다른 알림을 처리 중입니다. 잠시 후 다시 시도해 주세요.'; return; }
      if (result.retryable) { error = '알림을 열지 못했습니다. 연결 상태를 확인한 후 다시 시도해 주세요.'; move(null); return; }
      pending = null; forget(job.key); move(result.destination);
      await clear().catch(() => {});
    } catch {
      if (ticket === generation) error = '알림을 열지 못했습니다. 다시 시도해 주세요.';
    } finally { if (ticket === generation) { working = false; publish(); } }
  }
  return {
    snapshot: () => state,
    subscribe(fn: () => void) { listeners.add(fn); return () => { listeners.delete(fn); }; },
    receive(response: PushResponse) {
      if (completed.has(response.key) || pending?.key === response.key) return;
      const id = notificationId(response.value);
      if (id === null) { forget(response.key); void clear().catch(() => {}); return; }
      if (pending?.id === id) return;
      generation++; working = false; pending = { key: response.key, id }; error = ''; publish(); void drain();
    },
    session(next: number | null) {
      if (member === next) return;
      // Anonymous clicks survive login; an authenticated account's click never survives logout/switch.
      if (member !== null) { if (pending) forget(pending.key); pending = null; void clear().catch(() => {}); }
      generation++; working = false; error = ''; member = next; publish(); void drain();
    },
    attach(action: ClickHandler, move: (id: number | null) => void) { handler = action; navigate = move; ready = true; void drain(); return () => { ready = false; handler = null; navigate = null; }; },
    retry: drain,
  };
}
