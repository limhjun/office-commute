import { useState } from 'react';
import {
  Button, Group, Modal, Select, Stack, Table, TextInput, Title, Text, Card, Badge, PasswordInput,
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { IconPlus } from '@tabler/icons-react';
import { useEmployees, useCreateEmployee, useChangeEmployeeTeam } from '@/hooks/useEmployees';
import { useTeams } from '@/hooks/useTeams';
import { ApiError } from '@/lib/errors';
import { notifyError, notifySuccess } from '@/lib/notify';
import type { schemas } from '@/api/types';

function toIsoDate(d: Date | null): string {
  if (!d) return '';
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function EmployeesPage() {
  const { data: employees, isLoading } = useEmployees();
  const { data: teams } = useTeams();
  const createEmployee = useCreateEmployee();
  const changeTeam = useChangeEmployeeTeam();
  const [opened, setOpened] = useState(false);

  const teamOptions = (teams ?? []).map((t) => ({ value: String(t.teamId), label: t.name }));

  const form = useForm({
    initialValues: {
      name: '', role: 'MEMBER' as schemas['Role'], birthday: null as Date | null,
      workStartDate: null as Date | null, employeeCode: '', email: '', password: '',
      teamId: '' as string,
    },
    validate: {
      name: (v) => (v.trim() ? null : '이름을 입력하세요.'),
      employeeCode: (v) => (/^[A-Z0-9]{6,10}$/.test(v) ? null : '사번은 대문자/숫자 6–10자입니다.'),
      email: (v) => (/^\S+@\S+$/.test(v) ? null : '이메일 형식을 확인하세요.'),
      password: (v) => (v.length >= 8 ? null : '비밀번호는 8자 이상입니다.'),
      birthday: (v) => (v ? null : '생일을 선택하세요.'),
      workStartDate: (v) => (v ? null : '입사일을 선택하세요.'),
    },
  });

  async function onSubmit(values: typeof form.values) {
    try {
      await createEmployee.mutateAsync({
        name: values.name.trim(),
        role: values.role,
        birthday: toIsoDate(values.birthday),
        workStartDate: toIsoDate(values.workStartDate),
        employeeCode: values.employeeCode,
        email: values.email.trim(),
        password: values.password,
        teamId: values.teamId ? Number(values.teamId) : null,
      });
      notifySuccess('직원을 등록했습니다.');
      setOpened(false);
      form.reset();
    } catch (e) {
      if (e instanceof ApiError && e.code === 'EMPLOYEE_ALREADY_EXISTS') {
        form.setFieldError('employeeCode', '사번 또는 이메일이 이미 사용 중입니다.');
        return;
      }
      if (e instanceof ApiError) form.setErrors(e.fieldErrors);
      notifyError(e);
    }
  }

  async function onChangeTeam(employeeId: number, teamId: string | null) {
    try {
      await changeTeam.mutateAsync({ employeeId, teamId: teamId ? Number(teamId) : null });
      notifySuccess('소속 팀을 변경했습니다.');
    } catch (e) {
      notifyError(e);
    }
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={3}>직원</Title>
        <Button leftSection={<IconPlus size={16} />} onClick={() => setOpened(true)}>직원 등록</Button>
      </Group>

      <Card withBorder p={0}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>이름</Table.Th>
              <Table.Th>사번</Table.Th>
              <Table.Th>역할</Table.Th>
              <Table.Th>이메일</Table.Th>
              <Table.Th>소속 팀</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {employees?.map((e) => (
              <Table.Tr key={e.employeeId}>
                <Table.Td>{e.name}</Table.Td>
                <Table.Td>{e.employeeCode}</Table.Td>
                <Table.Td>
                  <Badge variant="light" color={e.role === 'MANAGER' ? 'indigo' : 'gray'}>
                    {e.role === 'MANAGER' ? '매니저' : '멤버'}
                  </Badge>
                </Table.Td>
                <Table.Td>{e.email}</Table.Td>
                <Table.Td>
                  <Select
                    size="xs"
                    placeholder="미배정"
                    clearable
                    data={teamOptions}
                    value={e.teamId ? String(e.teamId) : null}
                    onChange={(v) => onChangeTeam(e.employeeId, v)}
                    w={160}
                  />
                </Table.Td>
              </Table.Tr>
            ))}
            {!isLoading && employees?.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={5}>
                  <Text c="dimmed" ta="center" py="lg">등록된 직원이 없습니다.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Card>

      <Modal opened={opened} onClose={() => setOpened(false)} title="직원 등록" centered size="lg">
        <form onSubmit={form.onSubmit(onSubmit)}>
          <Stack>
            <Group grow>
              <TextInput label="이름" withAsterisk {...form.getInputProps('name')} />
              <Select
                label="역할" withAsterisk
                data={[{ value: 'MEMBER', label: '멤버' }, { value: 'MANAGER', label: '매니저' }]}
                {...form.getInputProps('role')}
              />
            </Group>
            <Group grow>
              <TextInput label="사번" placeholder="ABC123" withAsterisk {...form.getInputProps('employeeCode')} />
              <TextInput label="이메일" withAsterisk {...form.getInputProps('email')} />
            </Group>
            <Group grow>
              <DateInput label="생일" valueFormat="YYYY-MM-DD" withAsterisk {...form.getInputProps('birthday')} />
              <DateInput label="입사일" valueFormat="YYYY-MM-DD" withAsterisk {...form.getInputProps('workStartDate')} />
            </Group>
            <Group grow>
              <PasswordInput label="비밀번호" withAsterisk {...form.getInputProps('password')} />
              <Select label="소속 팀" placeholder="미배정" clearable data={teamOptions} {...form.getInputProps('teamId')} />
            </Group>
            <Group justify="flex-end">
              <Button variant="default" onClick={() => setOpened(false)}>취소</Button>
              <Button type="submit" loading={createEmployee.isPending}>등록</Button>
            </Group>
          </Stack>
        </form>
      </Modal>
    </Stack>
  );
}
