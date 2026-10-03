import { useCallback, useMemo, useSyncExternalStore } from 'react';
import { useFocusEffect } from 'expo-router';
import { apiClient } from '@/auth/runtime';
import { createLearningApi, learningError } from './api';
import { createResource } from './resource';
export const learningApi = createLearningApi(apiClient);
export function useLearning<T>(load: (signal: AbortSignal) => Promise<T>) {
  const resource = useMemo(() => {
    const value = createResource<T>(learningError);
    return { ...value, reload: () => value.run(load) };
  }, [load]);
  const state = useSyncExternalStore(resource.subscribe, resource.snapshot, resource.snapshot);
  const reload = resource.reload;
  useFocusEffect(useCallback(() => { void reload(); return () => resource.cancel(); }, [reload, resource]));
  return { ...state, reload, run: resource.run };
}
