import { useState } from 'react';
import {
  Button, Group, Modal, Select, Stack, Table, TextInput, Title, Text, Card, Badge, PasswordInput,
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { IconPlus } from '@tabler/icons-react';
import {
  useEmployees, useCreateEmployee, useChangeEmployeeTeam, useAssignCorrectionApprover,
} from '@/hooks/useEmployees';
import { useTeams } from '@/hooks/useTeams';
import { TableStateRow } from '@/components/TableStateRow';
import { ApiError } from '@/lib/errors';
import { notifyError, notifySuccess } from '@/lib/notify';
import { roleLabel } from '@/lib/roles';
import type { schemas } from '@/api/types';

function toIsoDate(d: Date | null): string {
  if (!d) return '';
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

type Employee = schemas['EmployeeFindResponse'];

// 매니저의 정정은 상위 승인자가, 상위 승인자의 정정은 매니저가 처리한다. 멤버는 모든 매니저가 처리하므로 지정하지 않는다.
const APPROVER_ROLE_FOR: Partial<Record<Employee['role'], Employee['role']>> = {
  MANAGER: 'COMMUTE_APPROVER',
  COMMUTE_APPROVER: 'MANAGER',
};

function ApproverCell({ employee, employees, onChange }: {
  employee: Employee;
  employees: Employee[];
  onChange: (employeeId: number, approverId: string | null) => void;
}) {
  const approverRole = APPROVER_ROLE_FOR[employee.role];
  if (!approverRole) return <Text size="sm" c="dimmed">전체 매니저</Text>;

  const options = employees
    .filter((c) => c.role === approverRole && c.employeeId !== employee.employeeId)
    .map((c) => ({ value: String(c.employeeId), label: `${c.name} (${c.employeeCode})` }));
  const current = employee.correctionApprover;
  // 현재 담당자가 후보에 없으면(역할 변경 등) 표시용으로 남겨 둔다
  if (current && !options.some((o) => o.value === String(current.employeeId))) {
    options.push({ value: String(current.employeeId), label: `${current.name} (${current.employeeCode})` });
  }

  return (
    <Select
      size="xs"
      placeholder="미지정"
      clearable
      data={options}
      value={current ? String(current.employeeId) : null}
      onChange={(v) => onChange(employee.employeeId, v)}
      error={!current}
      nothingFoundMessage={approverRole === 'MANAGER' ? '지정할 매니저가 없습니다' : '지정할 상위 승인자가 없습니다'}
      w={200}
    />
  );
}

export function EmployeesPage() {
  const { data: employees, isLoading, error, refetch } = useEmployees();
  const { data: teams } = useTeams();
  const createEmployee = useCreateEmployee();
  const changeTeam = useChangeEmployeeTeam();
  const assignApprover = useAssignCorrectionApprover();
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

  async function onChangeApprover(employeeId: number, approverId: string | null) {
    try {
      await assignApprover.mutateAsync({ employeeId, approverId: approverId ? Number(approverId) : null });
      notifySuccess(approverId ? '정정 승인 담당자를 지정했습니다.' : '정정 승인 담당자 지정을 해제했습니다.');
    } catch (e) {
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
              <Table.Th>정정 승인 담당자</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {employees?.map((e) => (
              <Table.Tr key={e.employeeId}>
                <Table.Td>{e.name}</Table.Td>
                <Table.Td>{e.employeeCode}</Table.Td>
                <Table.Td>
                  <Badge variant="light" color={roleLabel(e.role).color}>{roleLabel(e.role).label}</Badge>
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
                <Table.Td>
                  <ApproverCell employee={e} employees={employees} onChange={onChangeApprover} />
                </Table.Td>
              </Table.Tr>
            ))}
            <TableStateRow
              colSpan={6}
              isLoading={isLoading}
              error={error}
              isEmpty={employees?.length === 0}
              emptyText="등록된 직원이 없습니다."
              onRetry={() => refetch()}
            />
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
                data={[
                  { value: 'MEMBER', label: '멤버' },
                  { value: 'MANAGER', label: '매니저' },
                  { value: 'COMMUTE_APPROVER', label: '상위 승인자 (정정 승인 전용)' },
                ]}
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
