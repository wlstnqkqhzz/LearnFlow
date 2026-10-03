import { createContext, useContext, useEffect, useState, useSyncExternalStore, type PropsWithChildren } from 'react';
import { AppState } from 'react-native';
import { apiClient } from '@/auth/runtime';
import { createNotificationApi, foregroundHandler } from './api';
import { createNotificationStore } from './store';
const Context = createContext<ReturnType<typeof createNotificationStore> | null>(null);
export function NotificationProvider({ children, enabled }: PropsWithChildren<{ enabled: boolean }>) {
  const [store] = useState(() => createNotificationStore(createNotificationApi(apiClient)));
  useEffect(() => {
    if (!enabled) return;
    void store.start();
    const subscription = AppState.addEventListener('change', foregroundHandler(() => { void store.refresh(); }, AppState.currentState));
    return () => { subscription.remove(); store.stop(); };
  }, [enabled, store]);
  return <Context.Provider value={store}>{children}</Context.Provider>;
}
export function useNotifications() {
  const store = useContext(Context);
  if (!store) throw new Error('NotificationProvider is required');
  const state = useSyncExternalStore(store.subscribe, store.snapshot, store.snapshot);
  return { ...state, store };
}
