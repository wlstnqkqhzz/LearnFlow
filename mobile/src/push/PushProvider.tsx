import { useEffect, useSyncExternalStore, type PropsWithChildren } from 'react';
import { AppState } from 'react-native';
import { useRootNavigationState, useRouter } from 'expo-router';
import { authSession } from '@/auth/runtime';
import { useAuth } from '@/auth/AuthProvider';
import { useNotifications } from '@/notifications/NotificationProvider';
import { foregroundHandler } from '@/notifications/api';
import { pushClicks, pushStore } from './runtime';
import { createPushApi } from './api';
import { listenPush } from './native';

const receivers = new Set<() => void>();
export function PushProvider({ children }: PropsWithChildren) {
  useEffect(() => {
    const sync = () => {
      const state = authSession.getSnapshot();
      const member = state.status === 'authenticated' && state.user?.roles.includes('EMPLOYEE') ? state.user.memberId : null;
      pushClicks.session(member); void pushStore.setMember(member);
    };
    const unsubscribe = authSession.subscribe(sync);
    const invalidation = authSession.onInvalidate(access => { void pushStore.logout(createPushApi(authSession.publicClient, access)); });
    sync();
    const app = AppState.addEventListener('change', foregroundHandler(() => { void pushStore.reconcile(); }, AppState.currentState));
    let stopped = false, dispose: (() => void) | undefined;
    void listenPush(() => receivers.forEach(fn => fn()), value => { if (!stopped) pushClicks.receive(value); }, () => { void pushStore.reconcile(); })
      .then(value => { if (stopped) value(); else dispose = value; }).catch(() => {});
    return () => { stopped = true; dispose?.(); app.remove(); unsubscribe(); invalidation(); };
  }, []);
  return children;
}
export function PushNavigationBridge() {
  const { store } = useNotifications(); const { status, user } = useAuth();
  const root = useRootNavigationState(); const router = useRouter();
  useEffect(() => {
    if (status !== 'authenticated' || !root?.key) return;
    const refresh = () => { void store.refresh(); };
    receivers.add(refresh);
    const detach = pushClicks.attach(async id => {
      if (store.snapshot().working) return { destination: null, retryable: false, busy: true };
      const destination = await store.select(id);
      return { destination, retryable: store.snapshot().actionRetryable };
    }, id => {
      if (id === null) router.navigate('/notifications');
      else router.push({ pathname: '/enrollment/[id]', params: { id } });
    });
    return () => { receivers.delete(refresh); detach(); };
  }, [status, user?.memberId, root?.key, router, store]);
  return null;
}
export function usePushSettings() {
  const state = useSyncExternalStore(pushStore.subscribe, pushStore.snapshot, pushStore.snapshot);
  const click = useSyncExternalStore(pushClicks.subscribe, pushClicks.snapshot, pushClicks.snapshot);
  return { ...state, click, store: pushStore, clicks: pushClicks };
}
