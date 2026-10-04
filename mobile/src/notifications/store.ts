import type { NotificationPage, createNotificationApi } from './api';
import { resolveNotification, unavailable } from './resolve.ts';
type State = { data: NotificationPage | null; count: number | null; page: number; loading: boolean; countLoading: boolean; error: string; countError: string; working: boolean; actionError: string; actionRetryable: boolean };
export function createNotificationStore(api: ReturnType<typeof createNotificationApi>) {
  const initial = (): State => ({ data: null, count: null, page: 0, loading: false, countLoading: false, error: '', countError: '', working: false, actionError: '', actionRetryable: false });
  let state = initial(), active = false, stopped = false, generation = 0;
  let request: { controller: AbortController; list: boolean; promise: Promise<void> } | null = null;
  let operation: AbortController | null = null;
  const listeners = new Set<() => void>();
  const publish = (patch: Partial<State>) => { if (!stopped) { state = { ...state, ...patch }; listeners.forEach(fn => fn()); } };
  function cancel() { generation++; request?.controller.abort(); request = null; }
  function refresh(list = active) {
    if (stopped) return Promise.resolve();
    if (request && (!list || request.list)) return request.promise;
    cancel(); const ticket = generation, page = state.page, controller = new AbortController();
    publish({ loading: list, countLoading: true, error: list ? '' : state.error, countError: '' });
    const promise = Promise.allSettled([api.count(controller.signal), list ? api.list(page, controller.signal) : Promise.resolve(null)]).then(([count, items]) => {
      if (ticket !== generation || stopped) return;
      publish({ count: count.status === 'fulfilled' ? count.value : null, countLoading: false, countError: count.status === 'rejected' ? '미읽음 수를 불러오지 못했습니다.' : '', loading: false,
        ...(list ? { data: items.status === 'fulfilled' ? items.value : null, error: items.status === 'rejected' ? '알림을 불러오지 못했습니다. 연결을 확인하고 다시 시도해 주세요.' : '' } : {}) });
    }).finally(() => { if (ticket === generation) request = null; });
    request = { controller, list, promise }; return promise;
  }
  async function act(id?: number): Promise<number | null> {
    if (state.working || stopped) return null;
    cancel(); const controller = new AbortController(); operation = controller;
    publish({ working: true, loading: false, countLoading: false, actionError: '', actionRetryable: false });
    let destination: number | null = null;
    try {
      if (id === undefined) {
        await api.readAll(controller.signal);
        publish({ count: null });
      } else {
        const result = await resolveNotification(api, id, controller.signal);
        const { notification } = result;
        if (controller.signal.aborted) return null;
        publish({ data: state.data ? { ...state.data, content: state.data.content.map(item => item.notificationId === id ? notification : item) } : null, count: null });
        destination = result.destination;
        publish({ actionError: result.message, actionRetryable: result.retryable });
      }
    } catch (error) { if (!controller.signal.aborted) publish({ actionError: unavailable(error) ? '이 알림에 접근할 수 없습니다.' : '알림을 처리하지 못했습니다. 새로고침 후 다시 시도해 주세요.', actionRetryable: !unavailable(error) }); }
    finally {
      if (!controller.signal.aborted && !stopped) await refresh(active);
      if (operation === controller) { operation = null; publish({ working: false }); }
    }
    return controller.signal.aborted || stopped ? null : destination;
  }
  return {
    snapshot: () => state,
    subscribe(fn: () => void) { listeners.add(fn); return () => { listeners.delete(fn); }; },
    start() { stopped = false; return refresh(active); },
    stop() { cancel(); operation?.abort(); operation = null; publish(initial()); stopped = true; active = false; },
    focus(value: boolean) { active = value; if (value && !state.working) return refresh(true); return Promise.resolve(); },
    refresh() { return state.working ? Promise.resolve() : refresh(); },
    page(value: number) { if (state.working || state.loading || value < 0 || !Number.isSafeInteger(value)) return Promise.resolve(); cancel(); publish({ page: value, data: null }); return refresh(true); },
    select: (id: number) => act(id),
    readAll: () => act(),
  };
}
