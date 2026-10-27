import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import type { Schema } from '../../shared/api/client';
import { ApiFailure, resetCsrf } from '../../shared/api/client';
import { api } from '../../shared/api/platform';
import { ErrorState, Loading } from '../../shared/ui/States';

interface AuthContextValue {
  user: Schema<'Profile'> | null;
  setUser: (user: Schema<'Profile'> | null) => void;
  logout: () => Promise<void>;
}
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Schema<'Profile'> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    api.profile(controller.signal).then(
      (p) => {
        if (!controller.signal.aborted) {
          setUser(p);
          setLoading(false);
          setError(undefined);
        }
      },
      (e) => {
        if (!controller.signal.aborted) {
          setUser(null);
          setError(e instanceof ApiFailure && e.status === 401 ? undefined : e);
          setLoading(false);
        }
      },
    );
    return () => controller.abort();
  }, [version]);
  useEffect(() => {
    const expire = () => {
      setUser(null);
      resetCsrf();
    };
    window.addEventListener('linguatrack:session-expired', expire);
    return () => window.removeEventListener('linguatrack:session-expired', expire);
  }, []);
  const logout = useCallback(async () => {
    await api.logout();
    setUser(null);
  }, []);
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
