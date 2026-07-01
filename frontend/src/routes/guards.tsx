import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Center, Loader } from '@mantine/core';
import { useAuth } from '@/auth/AuthContext';

export function RequireAuth() {
  const { user, isLoading } = useAuth();
  const location = useLocation();
  if (isLoading) return <FullPageLoader />;
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />;
  return <Outlet />;
}

export function RequireManager() {
  const { user, isLoading, isManager } = useAuth();
  if (isLoading) return <FullPageLoader />;
  if (!user) return <Navigate to="/login" replace />;
  if (!isManager) return <Navigate to="/me/commute" replace />;
  return <Outlet />;
}

function FullPageLoader() {
  return (
    <Center h="100vh">
      <Loader />
    </Center>
  );
}
