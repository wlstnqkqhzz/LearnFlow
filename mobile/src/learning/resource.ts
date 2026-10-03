// Lifecycle-safe request state shared by focus reload, pull-to-refresh and retry.
export function createResource<T>(message: (error: unknown) => string) {
  let state: { data: T | null; loading: boolean; error: string } = { data: null, loading: true, error: '' };
  let generation = 0;
  let active: { controller: AbortController; promise: Promise<void> } | null = null;
  const listeners = new Set<() => void>();
  const publish = (next: typeof state) => { state = next; listeners.forEach(fn => fn()); };
  return {
    snapshot: () => state,
    subscribe: (fn: () => void) => { listeners.add(fn); return () => { listeners.delete(fn); }; },
    cancel() { generation++; active?.controller.abort(); active = null; },
    run(load: (signal: AbortSignal) => Promise<T>) {
      if (active) return active.promise;
      const ticket = ++generation, controller = new AbortController();
      publish({ ...state, loading: true, error: '' });
      const promise = Promise.resolve().then(() => load(controller.signal)).then(data => {
        if (generation === ticket) publish({ data, loading: false, error: '' });
      }, error => {
        if (generation === ticket) publish({ data: null, loading: false, error: message(error) });
      }).finally(() => { if (generation === ticket) active = null; });
      active = { controller, promise }; return promise;
    },
  };
}
