import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';

import '@mantine/core/styles.css';
import '@mantine/dates/styles.css';
import '@mantine/notifications/styles.css';

import App from './App';
import { theme } from './theme';
import { AuthProvider } from './auth/AuthContext';
import { ApiError } from './lib/errors';

// 역할은 서버가 매 요청 DB 에서 다시 읽는다. 세션 중 역할이 바뀌어 403 이 나면
// /api/auth/me 를 다시 읽어 메뉴·가드를 현재 역할에 맞춘다.
function refreshRoleOnForbidden(error: unknown) {
  if (error instanceof ApiError && error.status === 403 && error.code === 'FORBIDDEN') {
    void queryClient.invalidateQueries({ queryKey: ['auth', 'me'] });
  }
}

const queryClient: QueryClient = new QueryClient({
  queryCache: new QueryCache({ onError: refreshRoleOnForbidden }),
  mutationCache: new MutationCache({ onError: refreshRoleOnForbidden }),
  defaultOptions: {
    queries: {
      // 401 은 재시도해도 소용없다. 나머지는 1회만 재시도.
      retry: (failureCount, error) => {
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) return false;
        return failureCount < 1;
      },
      refetchOnWindowFocus: false,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <MantineProvider theme={theme} defaultColorScheme="auto">
      <Notifications position="top-right" />
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
        <ReactQueryDevtools initialIsOpen={false} />
      </QueryClientProvider>
    </MantineProvider>
  </React.StrictMode>,
);
