import { AppShell, Burger, Group, NavLink, ScrollArea, Text, Button, Badge } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  IconUsersGroup, IconUser, IconClockHour4, IconCalendarStats, IconReportAnalytics, IconLogout,
} from '@tabler/icons-react';
import { useAuth } from '@/auth/AuthContext';

interface NavItem { label: string; to: string; icon: React.ReactNode; managerOnly?: boolean }

const NAV: NavItem[] = [
  { label: '팀', to: '/teams', icon: <IconUsersGroup size={18} />, managerOnly: true },
  { label: '직원', to: '/employees', icon: <IconUser size={18} />, managerOnly: true },
  { label: '초과근무·리포트', to: '/overtime', icon: <IconReportAnalytics size={18} />, managerOnly: true },
  { label: '내 근태', to: '/me/commute', icon: <IconClockHour4 size={18} /> },
  { label: '내 연차', to: '/me/annual-leave', icon: <IconCalendarStats size={18} /> },
];

export function AppLayout() {
  const [opened, { toggle }] = useDisclosure();
  const { user, isManager, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const items = NAV.filter((n) => !n.managerOnly || isManager);

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <AppShell
      header={{ height: 56 }}
      navbar={{ width: 240, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      padding="md"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Group gap="sm">
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" />
            <Text fw={700}>근태 관리</Text>
          </Group>
          <Group gap="sm">
            <Text size="sm" c="dimmed">{user?.name}</Text>
            <Badge variant="light" color={isManager ? 'indigo' : 'gray'}>
              {isManager ? '매니저' : '멤버'}
            </Badge>
            <Button size="xs" variant="subtle" leftSection={<IconLogout size={16} />} onClick={handleLogout}>
              로그아웃
            </Button>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="xs">
        <ScrollArea>
          {items.map((n) => (
            <NavLink
              key={n.to}
              component={Link}
              to={n.to}
              label={n.label}
              leftSection={n.icon}
              active={location.pathname.startsWith(n.to)}
              onClick={() => opened && toggle()}
            />
          ))}
        </ScrollArea>
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
