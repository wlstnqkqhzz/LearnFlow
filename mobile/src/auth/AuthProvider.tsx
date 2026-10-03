import { createContext, useContext, useEffect, useSyncExternalStore, type PropsWithChildren } from 'react';
import { authSession, configurationError } from './runtime';
const Context = createContext(authSession);
export function AuthProvider({ children }: PropsWithChildren) {
  useEffect(() => { if (!configurationError) void authSession.restore(); }, []);
  return <Context.Provider value={authSession}>{children}</Context.Provider>;
}
export function useAuth() {
  const session = useContext(Context);
  const state = useSyncExternalStore(session.subscribe, session.getSnapshot, session.getSnapshot);
  return { ...state, session, configurationError };
}
