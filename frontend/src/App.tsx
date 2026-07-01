import { Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from '@/components/AppLayout';
import { RequireAuth, RequireManager } from '@/routes/guards';
import { useAuth } from '@/auth/AuthContext';
import { LoginPage } from '@/pages/LoginPage';
import { TeamsPage } from '@/pages/TeamsPage';
import { EmployeesPage } from '@/pages/EmployeesPage';
import { OvertimePage } from '@/pages/OvertimePage';
import { MyCommutePage } from '@/pages/MyCommutePage';
import { MyAnnualLeavePage } from '@/pages/MyAnnualLeavePage';

// 로그인 직후 역할에 맞는 첫 화면으로 보낸다.
function HomeRedirect() {
  const { isManager } = useAuth();
  return <Navigate to={isManager ? '/overtime' : '/me/commute'} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route index element={<HomeRedirect />} />
          <Route path="me/commute" element={<MyCommutePage />} />
          <Route path="me/annual-leave" element={<MyAnnualLeavePage />} />

          <Route element={<RequireManager />}>
            <Route path="teams" element={<TeamsPage />} />
            <Route path="employees" element={<EmployeesPage />} />
            <Route path="overtime" element={<OvertimePage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
