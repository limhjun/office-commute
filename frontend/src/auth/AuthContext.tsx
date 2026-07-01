import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/lib/errors';
import type { schemas } from '@/api/types';

type CurrentUser = schemas['CurrentUserResponse'];

interface AuthState {
  user: CurrentUser | null;
  isLoading: boolean;
  isManager: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

// 세션이 이미 살아있는 상태에서 새로고침해도 로그인 상태를 복원할 수 있도록
// 부팅 시 /api/auth/me 를 한 번 조회한다.
async function fetchMe(): Promise<CurrentUser | null> {
  const res = await api.GET('/api/auth/me', {});
  if (res.response.status === 401) return null;
  return unwrap(res);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  const [ready, setReady] = useState(false);

  const meQuery = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: fetchMe,
    retry: false,
    staleTime: Infinity,
  });

  if (meQuery.isFetched && !ready) setReady(true);

  const login = useCallback(async (email: string, password: string) => {
    unwrap(await api.POST('/api/auth/login', { body: { email, password } }));
    await qc.invalidateQueries({ queryKey: ['auth', 'me'] });
    await meQuery.refetch();
  }, [qc, meQuery]);

  const logout = useCallback(async () => {
    await api.POST('/api/auth/logout', {});
    qc.clear();
    qc.setQueryData(['auth', 'me'], null);
  }, [qc]);

  const user = meQuery.data ?? null;

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading: !ready && meQuery.isLoading,
        isManager: user?.role === 'MANAGER',
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
