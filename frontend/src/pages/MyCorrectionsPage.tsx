import { useState } from 'react';
import { Button, Card, Group, Popover, Stack, Table, Text, Title } from '@mantine/core';
import { useCancelCorrection, useMyCorrections } from '@/hooks/useCommuteCorrections';
import { CorrectionStatusFilter, type CorrectionStatusFilterValue } from '@/components/correction/CorrectionStatusFilter';
import { EndTimeChange } from '@/components/correction/EndTimeChange';
import { ProcessedCell } from '@/components/correction/ProcessedCell';
import { RequestStatusBadge } from '@/components/correction/RequestStatusBadge';
import { TableStateRow } from '@/components/TableStateRow';
import { formatInstant } from '@/lib/month';
import { notifyError, notifySuccess } from '@/lib/notify';

function CancelButton({ requestId }: { requestId: number }) {
  const cancel = useCancelCorrection();
  const [opened, setOpened] = useState(false);

  async function onConfirm() {
    try {
      await cancel.mutateAsync(requestId);
      notifySuccess('신청을 취소했습니다.');
      setOpened(false);
    } catch (e) {
      notifyError(e);
      setOpened(false);
    }
  }

  return (
    <Popover opened={opened} onChange={setOpened} withArrow position="left">
      <Popover.Target>
        <Button size="xs" variant="light" color="gray" onClick={() => setOpened((o) => !o)}>취소</Button>
      </Popover.Target>
      <Popover.Dropdown>
        <Stack gap="xs">
          <Text size="sm">이 신청을 취소할까요? 취소 이력은 남습니다.</Text>
          <Group justify="flex-end" gap="xs">
            <Button size="xs" variant="default" onClick={() => setOpened(false)}>닫기</Button>
            <Button size="xs" color="red" loading={cancel.isPending} onClick={onConfirm}>신청 취소</Button>
          </Group>
        </Stack>
      </Popover.Dropdown>
    </Popover>
  );
}

export function MyCorrectionsPage() {
  const [status, setStatus] = useState<CorrectionStatusFilterValue>('ALL');
  const { data, isLoading, error, refetch } = useMyCorrections(status === 'ALL' ? undefined : status);

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={3}>내 정정 요청</Title>
        <CorrectionStatusFilter value={status} onChange={setStatus} />
      </Group>

      <Card withBorder p={0}>
        <Table striped>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>근무일</Table.Th>
              <Table.Th>퇴근 시각 변경</Table.Th>
              <Table.Th>사유</Table.Th>
              <Table.Th>신청</Table.Th>
              <Table.Th>상태</Table.Th>
              <Table.Th>처리</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {data?.map((r) => (
              <Table.Tr key={r.requestId}>
                <Table.Td>{r.workDate}</Table.Td>
                <Table.Td>
                  <EndTimeChange
                    workDate={r.workDate}
                    beforeEndTime={r.previousWorkEndTime ?? null}
                    afterEndTime={r.requestedWorkEndTime}
                    beforeMinutes={r.previousWorkingMinutes}
                    afterMinutes={r.requestedWorkingMinutes}
                  />
                </Table.Td>
                <Table.Td maw={260}><Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>{r.reason}</Text></Table.Td>
                <Table.Td><Text size="sm">{formatInstant(r.requestedAt)}</Text></Table.Td>
                <Table.Td><RequestStatusBadge status={r.status} /></Table.Td>
                <Table.Td><ProcessedCell request={r} /></Table.Td>
                <Table.Td ta="right">{r.actions.canCancel && <CancelButton requestId={r.requestId} />}</Table.Td>
              </Table.Tr>
            ))}
            <TableStateRow
              colSpan={7}
              isLoading={isLoading}
              error={error}
              isEmpty={data?.length === 0}
              emptyText="정정 요청이 없습니다."
              onRetry={() => refetch()}
            />
          </Table.Tbody>
        </Table>
      </Card>
    </Stack>
  );
}
