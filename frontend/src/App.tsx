import { Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from '@/components/AppLayout';
import { RequireAuth, RequireManager, RequireRole } from '@/routes/guards';
import { REVIEWER_ROLES } from '@/lib/roles';
import { useAuth } from '@/auth/AuthContext';
import { LoginPage } from '@/pages/LoginPage';
import { TeamsPage } from '@/pages/TeamsPage';
import { EmployeesPage } from '@/pages/EmployeesPage';
import { OvertimePage } from '@/pages/OvertimePage';
import { MyCommutePage } from '@/pages/MyCommutePage';
import { MyAnnualLeavePage } from '@/pages/MyAnnualLeavePage';
import { MyCorrectionsPage } from '@/pages/MyCorrectionsPage';
import { ApprovalsPage } from '@/pages/ApprovalsPage';
import { MonthlyClosingPage } from '@/pages/MonthlyClosingPage';

// 로그인 직후 역할에 맞는 첫 화면으로 보낸다.
function HomeRedirect() {
  const { isManager } = useAuth();
  return <Navigate to={isManager ? '/employees' : '/me/commute'} replace />;
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
          <Route path="me/corrections" element={<MyCorrectionsPage />} />

          <Route element={<RequireRole roles={REVIEWER_ROLES} />}>
            <Route path="approvals" element={<ApprovalsPage />} />
          </Route>

          <Route element={<RequireManager />}>
            <Route path="teams" element={<TeamsPage />} />
            <Route path="employees" element={<EmployeesPage />} />
            <Route path="overtime" element={<OvertimePage />} />
            <Route path="closing" element={<MonthlyClosingPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
