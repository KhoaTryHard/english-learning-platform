import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import type { Schema } from '../../shared/api/client';
import { ApiFailure, resetCsrf } from '../../shared/api/client';
import { api } from '../../shared/api/platform';
import { ErrorState, Loading } from '../../shared/ui/States';
import { rememberSession, restoreSession } from './session';

interface AuthContextValue {
  user: Schema<'Profile'> | null;
  setUser: (user: Schema<'Profile'> | null) => void;
  logout: () => Promise<void>;
}
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, updateUser] = useState<Schema<'Profile'> | null>(null);
  const setUser = useCallback((next: Schema<'Profile'> | null) => {
    rememberSession(next);
    updateUser(next);
  }, []);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    restoreSession(controller.signal).then(
      (p) => {
        if (!controller.signal.aborted) {
          setUser(p);
          setLoading(false);
          setError(undefined);
        }
      },
      (e) => {
        if (!controller.signal.aborted) {
          const invalid = e instanceof ApiFailure && [401, 403, 404].includes(e.status);
          // Preserve the non-secret Admin hint during an outage so Retry can validate it again.
          if (invalid) setUser(null);
          else updateUser(null);
          setError(invalid ? undefined : e);
          setLoading(false);
        }
      },
    );
    return () => controller.abort();
  }, [version, setUser]);
  useEffect(() => {
    const expire = () => {
      setUser(null);
      resetCsrf();
    };
    window.addEventListener('linguatrack:session-expired', expire);
    return () => window.removeEventListener('linguatrack:session-expired', expire);
  }, [setUser]);
  const logout = useCallback(async () => {
    await api.logout();
    setUser(null);
  }, [setUser]);
  if (loading) return <Loading text="Đang kiểm tra phiên học…" />;
  if (error)
    return (
      <ErrorState
        error={error}
        retry={() => {
          setLoading(true);
          setVersion((v) => v + 1);
        }}
      />
    );
  return <AuthContext value={{ user, setUser, logout }}>{children}</AuthContext>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('AuthProvider missing');
  return value;
}
