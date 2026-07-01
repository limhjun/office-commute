import { useState } from 'react';
import { Button, Card, Group, Stack, Title, Text, Table, Badge } from '@mantine/core';
import { DatePickerInput } from '@mantine/dates';
import { IconCalendarPlus } from '@tabler/icons-react';
import { useRemainingLeaves, useEnrollLeaves } from '@/hooks/useAnnualLeave';
import { ApiError } from '@/lib/errors';
import { notifyError, notifySuccess } from '@/lib/notify';

function toIsoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const LEAVE_ERROR_MESSAGES: Record<string, string> = {
  ANNUAL_LEAVE_CRITERIA_NOT_MET: '팀의 통보 기한을 충족하지 못했습니다. 더 앞선 날짜로 신청하세요.',
  ANNUAL_LEAVE_PAST_DATE: '과거 날짜는 신청할 수 없습니다.',
  ANNUAL_LEAVE_DUPLICATE: '이미 신청한 날짜가 포함되어 있습니다.',
  EMPLOYEE_WITHOUT_TEAM: '팀에 배정되어야 연차를 신청할 수 있습니다. 관리자에게 문의하세요.',
};

export function MyAnnualLeavePage() {
  const { data, isLoading } = useRemainingLeaves();
  const enroll = useEnrollLeaves();
  const [dates, setDates] = useState<Date[]>([]);

  async function onEnroll() {
    if (dates.length === 0) return;
    try {
      await enroll.mutateAsync(dates.map(toIsoDate));
      notifySuccess('연차를 신청했습니다.');
      setDates([]);
    } catch (e) {
      const msg = e instanceof ApiError ? LEAVE_ERROR_MESSAGES[e.code] : undefined;
      notifyError(e, msg ?? '연차 신청에 실패했습니다.');
    }
  }

  return (
    <Stack>
      <Title order={3}>내 연차</Title>

      <Card withBorder>
        <Text fw={500} mb="sm">연차 신청</Text>
        <Group align="flex-end">
          <DatePickerInput
            type="multiple"
            label="사용할 날짜"
            placeholder="여러 날짜 선택 가능"
            value={dates}
            onChange={setDates}
            valueFormat="YYYY-MM-DD"
            miw={280}
          />
          <Button
            leftSection={<IconCalendarPlus size={16} />}
            onClick={onEnroll}
            loading={enroll.isPending}
            disabled={dates.length === 0}
          >
            신청
          </Button>
        </Group>
      </Card>

      <Card withBorder>
        <Text fw={500} mb="sm">예정된 연차</Text>
        <Table striped>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>날짜</Table.Th>
              <Table.Th>상태</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {data?.remainingLeaves.map((l) => (
              <Table.Tr key={l.id}>
                <Table.Td>{l.wantedDate}</Table.Td>
                <Table.Td><Badge color="grape" variant="light">확정</Badge></Table.Td>
              </Table.Tr>
            ))}
            {!isLoading && data?.remainingLeaves.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={2}>
                  <Text c="dimmed" ta="center" py="lg">예정된 연차가 없습니다.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Card>
    </Stack>
  );
}
