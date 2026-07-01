import { useState } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import { Button, Card, Center, PasswordInput, Stack, TextInput, Title, Alert } from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconAlertCircle } from '@tabler/icons-react';
import { useAuth } from '@/auth/AuthContext';
import { ApiError } from '@/lib/errors';

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const form = useForm({
    initialValues: { email: '', password: '' },
    validate: {
      email: (v) => (/^\S+@\S+$/.test(v) ? null : '이메일 형식을 확인하세요.'),
      password: (v) => (v.length > 0 ? null : '비밀번호를 입력하세요.'),
    },
  });

  if (user) {
    const to = (location.state as { from?: Location })?.from?.pathname ?? '/';
    return <Navigate to={to} replace />;
  }

  async function onSubmit(values: { email: string; password: string }) {
    setError(null);
    setLoading(true);
    try {
      await login(values.email, values.password);
      navigate('/', { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '로그인에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Center h="100vh">
      <Card withBorder shadow="sm" p="xl" w={380}>
        <Title order={3} mb="lg">근태 관리 로그인</Title>
        <form onSubmit={form.onSubmit(onSubmit)}>
          <Stack>
            {error && (
              <Alert color="red" icon={<IconAlertCircle size={16} />}>{error}</Alert>
            )}
            <TextInput label="이메일" placeholder="admin@company.com" {...form.getInputProps('email')} />
            <PasswordInput label="비밀번호" {...form.getInputProps('password')} />
            <Button type="submit" loading={loading} fullWidth mt="sm">로그인</Button>
          </Stack>
        </form>
      </Card>
    </Center>
  );
}
