import { useState } from 'react';
import {
  Button, Card, Group, Stack, Table, Title, Text, Badge, Divider,
} from '@mantine/core';
import { MonthPickerInput } from '@mantine/dates';
import { IconLogin2, IconLogout2 } from '@tabler/icons-react';
import { useWorkDuration, useClockIn, useClockOut } from '@/hooks/useCommute';
import { currentYearMonth, toYearMonth, fromYearMonth, formatMinutes } from '@/lib/month';
import { notifyError, notifySuccess } from '@/lib/notify';

export function MyCommutePage() {
  const [ym, setYm] = useState(currentYearMonth());
  const { data, isLoading } = useWorkDuration(ym);
  const clockIn = useClockIn();
  const clockOut = useClockOut();

  async function onClockIn() {
    try { await clockIn.mutateAsync(); notifySuccess('출근을 기록했습니다.'); }
    catch (e) { notifyError(e); }
  }
  async function onClockOut() {
    try { await clockOut.mutateAsync(); notifySuccess('퇴근을 기록했습니다.'); }
    catch (e) { notifyError(e); }
  }

  return (
    <Stack>
      <Title order={3}>내 근태</Title>

      <Card withBorder>
        <Group justify="space-between">
          <Text fw={500}>오늘 근무 기록</Text>
          <Group>
            <Button leftSection={<IconLogin2 size={16} />} onClick={onClockIn} loading={clockIn.isPending}>출근</Button>
            <Button leftSection={<IconLogout2 size={16} />} variant="light" onClick={onClockOut} loading={clockOut.isPending}>퇴근</Button>
          </Group>
        </Group>
      </Card>

      <Card withBorder>
        <Group justify="space-between" mb="sm">
          <Text fw={500}>월별 근무 시간</Text>
          <MonthPickerInput
            value={fromYearMonth(ym)}
            onChange={(d) => setYm(toYearMonth(d))}
            valueFormat="YYYY년 M월"
            w={160}
          />
        </Group>
        <Divider mb="sm" />
        <Group justify="space-between" mb="md">
          <Text c="dimmed">이 달 총 근무</Text>
          <Text fw={700}>{data ? formatMinutes(data.sumWorkingMinutes) : '—'}</Text>
        </Group>
        <Table striped>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>날짜</Table.Th>
              <Table.Th>연차 사용</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {data?.details.map((d) => (
              <Table.Tr key={d.date}>
                <Table.Td>{d.date}</Table.Td>
                <Table.Td>
                  {d.usingDayOff ? <Badge color="grape" variant="light">연차</Badge> : <Text c="dimmed" span>근무</Text>}
                </Table.Td>
              </Table.Tr>
            ))}
            {!isLoading && data?.details.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={2}>
                  <Text c="dimmed" ta="center" py="lg">이 달 기록이 없습니다.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Card>
    </Stack>
  );
}
