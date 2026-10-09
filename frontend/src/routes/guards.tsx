import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Center, Loader } from '@mantine/core';
import { useAuth } from '@/auth/AuthContext';
import type { Role } from '@/lib/roles';

export function RequireAuth() {
  const { user, isLoading } = useAuth();
  const location = useLocation();
  if (isLoading) return <FullPageLoader />;
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />;
  return <Outlet />;
}

// UI 가드는 메뉴 노출용이다. 실제 권한은 서버가 다시 검사한다.
export function RequireRole({ roles }: { roles: Role[] }) {
  const { user, isLoading } = useAuth();
  if (isLoading) return <FullPageLoader />;
  if (!user) return <Navigate to="/login" replace />;
  if (!roles.includes(user.role)) return <Navigate to="/me/commute" replace />;
  return <Outlet />;
}

export function RequireManager() {
  return <RequireRole roles={['MANAGER']} />;
}

function FullPageLoader() {
  return (
    <Center h="100vh">
      <Loader />
    </Center>
  );
}
