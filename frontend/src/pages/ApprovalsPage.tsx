import { useState } from 'react';
import { Badge, Button, Card, Group, Stack, Table, Text, Title } from '@mantine/core';
import { useApproveCorrection, useRejectCorrection, useReviewCorrections } from '@/hooks/useCommuteCorrections';
import { CorrectionStatusFilter, type CorrectionStatusFilterValue } from '@/components/correction/CorrectionStatusFilter';
import { DecisionModal, type Decision } from '@/components/correction/DecisionModal';
import { EndTimeChange } from '@/components/correction/EndTimeChange';
import { ProcessedCell } from '@/components/correction/ProcessedCell';
import { RequestStatusBadge } from '@/components/correction/RequestStatusBadge';
import { TableStateRow } from '@/components/TableStateRow';
import { ZonedTimeText } from '@/components/ZonedTimeText';
import { ApiError } from '@/lib/errors';
import { formatInstant } from '@/lib/month';
import { notifyError, notifySuccess } from '@/lib/notify';
import { roleLabel } from '@/lib/roles';
import type { schemas } from '@/api/types';

type CorrectionRequest = schemas['CorrectionRequestResponse'];

function RequestSummary({ r }: { r: CorrectionRequest }) {
  return (
    <Card withBorder p="sm">
      <Stack gap={6}>
        <Group gap="xs">
          <Text fw={600}>{r.requester.name}</Text>
          <Text size="sm" c="dimmed">{r.requester.employeeCode}</Text>
          <Badge size="sm" variant="light" color={roleLabel(r.requesterRole).color}>{roleLabel(r.requesterRole).label}</Badge>
        </Group>
        <Group gap="lg">
          <Text size="sm">근무일 {r.workDate}</Text>
          <Text size="sm">출근 <ZonedTimeText iso={r.workStartTime} workDate={r.workDate} /></Text>
          <Text size="sm" c="dimmed">{r.workZone}</Text>
        </Group>
        <EndTimeChange
          workDate={r.workDate}
          beforeEndTime={r.previousWorkEndTime ?? null}
          afterEndTime={r.requestedWorkEndTime}
          beforeMinutes={r.previousWorkingMinutes}
          afterMinutes={r.requestedWorkingMinutes}
        />
        <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}><Text span c="dimmed">사유 </Text>{r.reason}</Text>
      </Stack>
    </Card>
  );
}

export function ApprovalsPage() {
  const [status, setStatus] = useState<CorrectionStatusFilterValue>('PENDING');
  const { data, isLoading, error, refetch } = useReviewCorrections(status === 'ALL' ? undefined : status);
  const approve = useApproveCorrection();
  const reject = useRejectCorrection();
  const [selected, setSelected] = useState<{ request: CorrectionRequest; decision: Decision } | null>(null);

  async function onDecide(comment: string | null) {
    if (!selected) return;
    const { request, decision } = selected;
    try {
      if (decision === 'approve') {
        await approve.mutateAsync({ requestId: request.requestId, comment });
        notifySuccess('정정 요청을 승인했습니다. 근태에 반영됐습니다.');
      } else {
        await reject.mutateAsync({ requestId: request.requestId, reason: comment ?? '' });
        notifySuccess('정정 요청을 반려했습니다.');
      }
      setSelected(null);
    } catch (e) {
      // 입력 오류(의견·사유 길이 등)만 칸에 붙이고, 나머지는 알림. 409 는 목록이 재조회된다.
      if (!(e instanceof ApiError) || !(e.fieldErrors.comment || e.fieldErrors.reason)) {
        notifyError(e);
        if (e instanceof ApiError && e.status === 409) setSelected(null);
      }
      throw e;
    }
  }

  function mapDecisionError(e: unknown): string | null {
    if (!(e instanceof ApiError)) return null;
    return e.fieldErrors.comment ?? e.fieldErrors.reason ?? null;
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={3}>정정 승인</Title>
        <CorrectionStatusFilter value={status} onChange={setStatus} />
      </Group>

      <Card withBorder p={0}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>신청자</Table.Th>
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
                <Table.Td>
                  <Stack gap={2}>
                    <Text size="sm">{r.requester.name}</Text>
                    <Group gap={4}>
                      <Text size="xs" c="dimmed">{r.requester.employeeCode}</Text>
                      {r.requesterRole !== 'MEMBER' && (
                        <Badge size="xs" variant="light" color={roleLabel(r.requesterRole).color}>{roleLabel(r.requesterRole).label}</Badge>
                      )}
                    </Group>
                  </Stack>
                </Table.Td>
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
                <Table.Td maw={240}><Text size="sm" lineClamp={3} style={{ whiteSpace: 'pre-wrap' }}>{r.reason}</Text></Table.Td>
                <Table.Td><Text size="sm">{formatInstant(r.requestedAt)}</Text></Table.Td>
                <Table.Td><RequestStatusBadge status={r.status} /></Table.Td>
                <Table.Td><ProcessedCell request={r} /></Table.Td>
                <Table.Td>
                  {r.actions.canReview && (
                    <Group gap={4} wrap="nowrap" justify="flex-end">
                      <Button size="xs" color="teal" variant="light" onClick={() => setSelected({ request: r, decision: 'approve' })}>승인</Button>
                      <Button size="xs" color="red" variant="light" onClick={() => setSelected({ request: r, decision: 'reject' })}>반려</Button>
                    </Group>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
            <TableStateRow
              colSpan={8}
              isLoading={isLoading}
              error={error}
              isEmpty={data?.length === 0}
              emptyText={status === 'PENDING' ? '처리할 정정 요청이 없습니다.' : '해당 상태의 요청이 없습니다.'}
              onRetry={() => refetch()}
            />
          </Table.Tbody>
        </Table>
      </Card>

      <DecisionModal
        decision={selected?.decision ?? null}
        onClose={() => setSelected(null)}
        onSubmit={onDecide}
        mapError={mapDecisionError}
        submitting={approve.isPending || reject.isPending}
      >
        {selected && <RequestSummary r={selected.request} />}
      </DecisionModal>
    </Stack>
  );
}
