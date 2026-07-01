import { useState } from 'react';
import {
  Button, Group, Modal, NumberInput, Stack, Table, TextInput, Title, Text, Card,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconPlus } from '@tabler/icons-react';
import { useTeams, useCreateTeam } from '@/hooks/useTeams';
import { ApiError } from '@/lib/errors';
import { notifyError, notifySuccess } from '@/lib/notify';

export function TeamsPage() {
  const { data: teams, isLoading } = useTeams();
  const createTeam = useCreateTeam();
  const [opened, setOpened] = useState(false);

  const form = useForm({
    initialValues: { teamName: '', managerName: '', annualLeaveCriteria: 0 },
    validate: { teamName: (v) => (v.trim() ? null : '팀 이름을 입력하세요.') },
  });

  async function onSubmit(values: typeof form.values) {
    try {
      await createTeam.mutateAsync({
        teamName: values.teamName.trim(),
        managerName: values.managerName.trim() || null,
        annualLeaveCriteria: values.annualLeaveCriteria,
      });
      notifySuccess('팀을 등록했습니다.');
      setOpened(false);
      form.reset();
    } catch (e) {
      if (e instanceof ApiError && e.code === 'TEAM_ALREADY_EXISTS') {
        form.setFieldError('teamName', '같은 이름의 팀이 이미 있습니다.');
        return;
      }
      if (e instanceof ApiError) form.setErrors(e.fieldErrors);
      notifyError(e);
    }
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={3}>팀</Title>
        <Button leftSection={<IconPlus size={16} />} onClick={() => setOpened(true)}>팀 등록</Button>
      </Group>

      <Card withBorder p={0}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>이름</Table.Th>
              <Table.Th>매니저</Table.Th>
              <Table.Th>연차 통보 기한(일)</Table.Th>
              <Table.Th>인원</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {teams?.map((t) => (
              <Table.Tr key={t.teamId}>
                <Table.Td>{t.name}</Table.Td>
                <Table.Td>{t.managerName ?? <Text c="dimmed" span>—</Text>}</Table.Td>
                <Table.Td>{t.annualLeaveCriteria}</Table.Td>
                <Table.Td>{t.memberCount}</Table.Td>
              </Table.Tr>
            ))}
            {!isLoading && teams?.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={4}>
                  <Text c="dimmed" ta="center" py="lg">등록된 팀이 없습니다. 첫 팀을 등록하세요.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Card>

      <Modal opened={opened} onClose={() => setOpened(false)} title="팀 등록" centered>
        <form onSubmit={form.onSubmit(onSubmit)}>
          <Stack>
            <TextInput label="팀 이름" withAsterisk {...form.getInputProps('teamName')} />
            <TextInput label="매니저 이름" placeholder="선택" {...form.getInputProps('managerName')} />
            <NumberInput label="연차 통보 기한(일)" min={0} {...form.getInputProps('annualLeaveCriteria')} />
            <Group justify="flex-end">
              <Button variant="default" onClick={() => setOpened(false)}>취소</Button>
              <Button type="submit" loading={createTeam.isPending}>등록</Button>
            </Group>
          </Stack>
        </form>
      </Modal>
    </Stack>
  );
}
