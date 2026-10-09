import { useState } from 'react';
import {
  Alert, Anchor, Badge, Button, Card, Group, List, Loader, SimpleGrid, Stack, Table, Text, Title,
} from '@mantine/core';
import { MonthPickerInput } from '@mantine/dates';
import { Link } from 'react-router-dom';
import {
  IconAlertTriangle, IconDownload, IconFileSpreadsheet, IconLock, IconLockOpen, IconSend,
} from '@tabler/icons-react';
import {
  useCloseMonth, useConfirmDispatch, useDispatchReport, useMonthlyClosings, useMonthlyClosingStatus,
} from '@/hooks/useMonthlyClosings';
import { CloseMonthConfirmModal, type CloseMonthPayload } from '@/components/closing/CloseMonthConfirmModal';
import { DispatchConfirmModal } from '@/components/closing/DispatchConfirmModal';
import { DispatchStatusBadge, ReportKindBadge } from '@/components/closing/DispatchStatusBadge';
import { TableStateRow } from '@/components/TableStateRow';
import { isHeldForClosing } from '@/lib/dispatch';
import { downloadFile } from '@/lib/download';
import { ApiError } from '@/lib/errors';
import { messageForError } from '@/lib/errorMessages';
import { formatInstant, fromYearMonth, toYearMonth } from '@/lib/month';
import { notifyError, notifySuccess, notifyWarning } from '@/lib/notify';
import { companyCurrentYearMonth, latestClosableYearMonth } from '@/lib/zonedTime';
import type { schemas } from '@/api/types';

type Dispatch = schemas['OverTimeReportDispatchResponse'];
type ClosingStatus = schemas['MonthlyClosingStatusResponse'];

const BLOCKER_LABELS: Record<string, string> = {
  MONTH_NOT_ENDED: '아직 끝나지 않은 월입니다. 회사 달력(Asia/Seoul) 기준으로 끝난 과거 월만 마감할 수 있습니다.',
  ALREADY_CLOSED: '이미 마감된 월입니다.',
  UNCLOSED_COMMUTES: '집계 기간에 퇴근이 기록되지 않은 근태가 있습니다.',
  PENDING_CORRECTIONS: '집계 기간에 승인 대기 중인 정정 요청이 있습니다.',
  DELIVERY_UNCERTAIN: '수신 여부를 확인하지 않은 보고서 발송이 있습니다. 먼저 수신 확인을 기록하세요.',
  PREVIOUS_LEGACY_MONTH_PENDING: '집계 기간이 겹치는 이전 기존 발송 월을 먼저 정리해야 합니다.',
};

const CLOSING_TYPE_LABELS: Record<string, string> = {
  MANUAL: '수동 마감',
  LEGACY_CONFIRMED: '기존 발송 월 등록 (정정 없음)',
  LEGACY_CORRECTED: '기존 발송 월 정정 후 마감',
};

function monthLabel(ym: string): string {
  const [y, m] = ym.split('-').map(Number);
  return `${y}년 ${m}월`;
}

// 마감 유형으로 정해지는 발송 종류가 아직 끝나지 않았으면 재시도할 수 있다.
function canRetryDispatch(status: ClosingStatus): boolean {
  const closing = status.closing;
  if (!closing || closing.type === 'LEGACY_CONFIRMED') return false;
  const kind = closing.type === 'LEGACY_CORRECTED' ? 'CORRECTION' : 'ORIGINAL';
  return !status.dispatches.some((d) => d.kind === kind && ['SENT', 'DELIVERY_COMMITTED', 'IN_PROGRESS'].includes(d.status));
}

function ClosingSummary({ status, onClose }: { status: ClosingStatus; onClose: () => void }) {
  const { closing } = status;
  return (
    <Card withBorder>
      <Group justify="space-between" align="flex-start">
        <Stack gap={4}>
          <Group gap="xs">
            <Text fw={600}>{monthLabel(status.yearMonth)}</Text>
            {closing
              ? <Badge color="gray" leftSection={<IconLock size={12} />}>마감됨</Badge>
              : status.closable
                ? <Badge color="teal" variant="light" leftSection={<IconLockOpen size={12} />}>마감 가능</Badge>
                : <Badge color="orange" variant="light">마감 불가</Badge>}
            {status.legacyDispatch && !closing && <Badge color="grape" variant="light">기존 발송 월</Badge>}
          </Group>
          <Text size="sm" c="dimmed">
            보고서 집계 기간 {status.rangeStart} ~ {status.rangeEnd} (전월 말 포함 가능)
          </Text>
        </Stack>
        {!closing && (
          <Button color="orange" leftSection={<IconLock size={16} />} disabled={!status.closable} onClick={onClose}>
            월 마감
          </Button>
        )}
      </Group>

      {closing && (
        <SimpleGrid cols={{ base: 1, sm: 3 }} mt="md">
          <Stack gap={0}><Text size="xs" c="dimmed">유형</Text><Text size="sm">{CLOSING_TYPE_LABELS[closing.type] ?? closing.type}</Text></Stack>
          <Stack gap={0}><Text size="xs" c="dimmed">마감 담당자</Text><Text size="sm">{closing.closedBy.name} ({closing.closedBy.employeeCode})</Text></Stack>
          <Stack gap={0}><Text size="xs" c="dimmed">마감 시각</Text><Text size="sm">{formatInstant(closing.closedAt)}</Text></Stack>
          {closing.note && (
            <Stack gap={0} style={{ gridColumn: '1 / -1' }}>
              <Text size="xs" c="dimmed">메모</Text>
              <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>{closing.note}</Text>
            </Stack>
          )}
        </SimpleGrid>
      )}

      {!closing && status.blockers.length > 0 && (
        <Alert mt="md" color="orange" variant="light" icon={<IconAlertTriangle size={16} />} title="마감할 수 없는 이유">
          <List size="sm" spacing={2}>
            {status.blockers.map((b) => <List.Item key={b}>{BLOCKER_LABELS[b] ?? b}</List.Item>)}
          </List>
        </Alert>
      )}
      {!closing && status.legacyDispatch && (
        <Alert mt="md" color="grape" variant="light" title="기능 도입 전에 보고서가 발송된 월">
          마감할 때 기존 보고서의 정정 여부와 확인 내용을 기록해야 합니다. 기존 발송 이력은 지우지 않으며,
          정정한 경우 정정본을 원본과 구분해 별도로 발송합니다.
        </Alert>
      )}
    </Card>
  );
}

function UnresolvedTables({ status }: { status: ClosingStatus }) {
  if (status.unclosedCommutes.length === 0 && status.pendingCorrections.length === 0) return null;
  return (
    <SimpleGrid cols={{ base: 1, md: 2 }}>
      <Card withBorder p={0}>
        <Text fw={500} p="sm">퇴근 미기록 ({status.unclosedCommutes.length})</Text>
        <Table>
          <Table.Thead><Table.Tr><Table.Th>직원</Table.Th><Table.Th>근무일</Table.Th></Table.Tr></Table.Thead>
          <Table.Tbody>
            {status.unclosedCommutes.map((u) => (
              <Table.Tr key={u.commuteHistoryId}>
                <Table.Td>{u.employee.name} <Text span size="xs" c="dimmed">{u.employee.employeeCode}</Text></Table.Td>
                <Table.Td>{u.workDate}</Table.Td>
              </Table.Tr>
            ))}
            {status.unclosedCommutes.length === 0 && (
              <Table.Tr><Table.Td colSpan={2}><Text size="sm" c="dimmed" ta="center">없음</Text></Table.Td></Table.Tr>
            )}
          </Table.Tbody>
        </Table>
        <Text size="xs" c="dimmed" p="sm">본인이 정정 신청하고 승인받아야 해소됩니다.</Text>
      </Card>
      <Card withBorder p={0}>
        <Group justify="space-between" p="sm">
          <Text fw={500}>승인 대기 정정 ({status.pendingCorrections.length})</Text>
          <Anchor component={Link} to="/approvals" size="sm">정정 승인으로</Anchor>
        </Group>
        <Table>
          <Table.Thead><Table.Tr><Table.Th>신청자</Table.Th><Table.Th>근무일</Table.Th></Table.Tr></Table.Thead>
          <Table.Tbody>
            {status.pendingCorrections.map((p) => (
              <Table.Tr key={p.requestId}>
                <Table.Td>{p.requester.name} <Text span size="xs" c="dimmed">{p.requester.employeeCode}</Text></Table.Td>
                <Table.Td>{p.workDate}</Table.Td>
              </Table.Tr>
            ))}
            {status.pendingCorrections.length === 0 && (
              <Table.Tr><Table.Td colSpan={2}><Text size="sm" c="dimmed" ta="center">없음</Text></Table.Td></Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Card>
    </SimpleGrid>
  );
}

export function MonthlyClosingPage() {
  const [ym, setYm] = useState(latestClosableYearMonth());
  const statusQuery = useMonthlyClosingStatus(ym);
  const closings = useMonthlyClosings();
  const closeMonth = useCloseMonth();
  const dispatchReport = useDispatchReport();
  const confirmDispatch = useConfirmDispatch();
  const [closeTarget, setCloseTarget] = useState<string | null>(null);
  const [confirmTarget, setConfirmTarget] = useState<{ yearMonth: string; kind: Dispatch['kind'] } | null>(null);
  const [downloading, setDownloading] = useState<string | null>(null);

  const status = statusQuery.data;

  async function onConfirmClose(payload: CloseMonthPayload) {
    if (!closeTarget) return;
    try {
      const res = await closeMonth.mutateAsync({ yearMonth: closeTarget, ...payload });
      setCloseTarget(null);
      const d = res.dispatch;
      if (!d) notifySuccess(`${monthLabel(closeTarget)}을 마감했습니다.`);
      else if (d.status === 'SENT') notifySuccess(`${monthLabel(closeTarget)}을 마감하고 보고서를 발송했습니다.`);
      else notifyWarning(`${monthLabel(closeTarget)}을 마감했지만 보고서 발송이 완료되지 않았습니다. 발송 상태를 확인하세요.`);
    } catch (e) {
      notifyError(e);
      // 상태가 바뀌었으면(미해결 생김·이미 마감) 재조회된 상태를 보도록 닫는다
      if (e instanceof ApiError && e.status === 409) setCloseTarget(null);
    }
  }

  async function onRetryDispatch() {
    try {
      const d = await dispatchReport.mutateAsync(ym);
      if (d.status === 'SENT') notifySuccess('보고서를 발송했습니다.');
      else if (isHeldForClosing(d.status, d.lastFailureReason)) notifyWarning('월 마감 전이라 발송하지 않았습니다.');
      else notifyWarning('보고서 발송이 완료되지 않았습니다. 발송 상태를 확인하세요.');
    } catch (e) {
      notifyError(e);
    }
  }

  async function onConfirmDelivery(outcome: schemas['DispatchConfirmationOutcome'], note: string) {
    if (!confirmTarget) return;
    try {
      await confirmDispatch.mutateAsync({ ...confirmTarget, outcome, note });
      notifySuccess(outcome === 'DELIVERED' ? '수신됨으로 기록했습니다.' : '미수신으로 기록했습니다. 재발송할 수 있습니다.');
      setConfirmTarget(null);
    } catch (e) {
      notifyError(e);
    }
  }

  async function download(key: string, url: string, fallbackName: string) {
    setDownloading(key);
    try {
      await downloadFile(url, fallbackName);
    } catch (e) {
      notifyError(e, '파일을 내려받지 못했습니다.');
    } finally {
      setDownloading(null);
    }
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={3}>월 마감 · 보고서 발송</Title>
        <Group>
          <MonthPickerInput
            value={fromYearMonth(ym)}
            onChange={(d) => setYm(toYearMonth(d))}
            maxDate={fromYearMonth(companyCurrentYearMonth())}
            valueFormat="YYYY년 M월"
            w={160}
          />
          <Button
            variant="default"
            leftSection={<IconFileSpreadsheet size={16} />}
            loading={downloading === 'reference'}
            onClick={() => download('reference', `/api/overtime/report/excel?yearMonth=${ym}`, `${ym}_초과근무보고서_참고용.xlsx`)}
          >
            참고용 Excel
          </Button>
        </Group>
      </Group>

      {statusQuery.isLoading && <Card withBorder><Group justify="center"><Loader size="sm" /></Group></Card>}
      {statusQuery.error && (
        <Alert color="red" title="마감 상태를 불러오지 못했습니다">
          <Group justify="space-between">
            <Text size="sm">{messageForError(statusQuery.error, '잠시 후 다시 시도하세요.')}</Text>
            <Button size="xs" variant="light" color="red" onClick={() => statusQuery.refetch()}>다시 시도</Button>
          </Group>
        </Alert>
      )}

      {status && (
        <>
          <ClosingSummary status={status} onClose={() => setCloseTarget(status.yearMonth)} />
          {!status.closing && <UnresolvedTables status={status} />}

          <Card withBorder p={0}>
            <Group justify="space-between" p="sm">
              <Text fw={500}>보고서 발송</Text>
              {canRetryDispatch(status) && (
                <Button size="xs" leftSection={<IconSend size={14} />} loading={dispatchReport.isPending} onClick={onRetryDispatch}>
                  발송 재시도
                </Button>
              )}
            </Group>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>구분</Table.Th>
                  <Table.Th>상태</Table.Th>
                  <Table.Th>시도</Table.Th>
                  <Table.Th>마지막 시도</Table.Th>
                  <Table.Th>발송 완료</Table.Th>
                  <Table.Th>비고</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {status.dispatches.map((d) => (
                  <Table.Tr key={d.kind}>
                    <Table.Td><ReportKindBadge kind={d.kind} /></Table.Td>
                    <Table.Td><DispatchStatusBadge status={d.status} lastFailureReason={d.lastFailureReason} /></Table.Td>
                    <Table.Td>{d.attemptCount}</Table.Td>
                    <Table.Td><Text size="sm">{formatInstant(d.lastAttemptedAt)}</Text></Table.Td>
                    <Table.Td><Text size="sm">{d.sentAt ? formatInstant(d.sentAt) : '—'}</Text></Table.Td>
                    <Table.Td maw={260}>
                      {d.status === 'FAILED' && !isHeldForClosing(d.status, d.lastFailureReason) && d.lastFailureReason && (
                        <Text size="xs" c="red" lineClamp={2}>{d.lastFailureReason}</Text>
                      )}
                      {d.deliveryConfirmedBy && (
                        <Text size="xs" c="dimmed">
                          수신 확인: {d.deliveryConfirmedBy.name} · {formatInstant(d.deliveryConfirmedAt)}
                          {d.deliveryConfirmationNote && ` — ${d.deliveryConfirmationNote}`}
                        </Text>
                      )}
                    </Table.Td>
                    <Table.Td>
                      <Group gap={4} justify="flex-end" wrap="nowrap">
                        {d.status === 'DELIVERY_COMMITTED' && (
                          <Button size="xs" color="orange" variant="light" onClick={() => setConfirmTarget({ yearMonth: d.yearMonth, kind: d.kind })}>
                            수신 확인
                          </Button>
                        )}
                        {d.finalFileAvailable && (
                          <Button
                            size="xs"
                            variant="light"
                            leftSection={<IconDownload size={14} />}
                            loading={downloading === d.kind}
                            onClick={() => download(
                              d.kind,
                              `/api/overtime/report/final-excel?yearMonth=${d.yearMonth}&kind=${d.kind}`,
                              `${d.yearMonth}_초과근무보고서${d.kind === 'CORRECTION' ? '_정정본' : ''}.xlsx`,
                            )}
                          >
                            확정본
                          </Button>
                        )}
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
                <TableStateRow
                  colSpan={7}
                  isLoading={false}
                  error={null}
                  isEmpty={status.dispatches.length === 0}
                  emptyText={status.closing ? '발송 이력이 없습니다.' : '마감 후 최종 보고서를 보관하고 발송합니다.'}
                />
              </Table.Tbody>
            </Table>
          </Card>
        </>
      )}

      <Card withBorder p={0}>
        <Text fw={500} p="sm">마감된 월</Text>
        <Table highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>월</Table.Th>
              <Table.Th>유형</Table.Th>
              <Table.Th>집계 기간</Table.Th>
              <Table.Th>마감</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {closings.data?.map((c) => (
              <Table.Tr key={c.yearMonth} style={{ cursor: 'pointer' }} onClick={() => setYm(c.yearMonth)}>
                <Table.Td>{monthLabel(c.yearMonth)}</Table.Td>
                <Table.Td>{CLOSING_TYPE_LABELS[c.type] ?? c.type}</Table.Td>
                <Table.Td>{c.rangeStart} ~ {c.rangeEnd}</Table.Td>
                <Table.Td><Text size="sm">{c.closedBy.name} · {formatInstant(c.closedAt)}</Text></Table.Td>
              </Table.Tr>
            ))}
            <TableStateRow
              colSpan={4}
              isLoading={closings.isLoading}
              error={closings.error}
              isEmpty={closings.data?.length === 0}
              emptyText="마감된 월이 없습니다."
              onRetry={() => closings.refetch()}
            />
          </Table.Tbody>
        </Table>
      </Card>

      <CloseMonthConfirmModal
        yearMonth={closeTarget}
        protectedFrom={status?.rangeStart}
        protectedTo={status?.rangeEnd}
        legacyDispatch={status?.legacyDispatch ?? false}
        onClose={() => setCloseTarget(null)}
        onConfirm={onConfirmClose}
        submitting={closeMonth.isPending}
      />
      <DispatchConfirmModal
        target={confirmTarget}
        onClose={() => setConfirmTarget(null)}
        onConfirm={onConfirmDelivery}
        submitting={confirmDispatch.isPending}
      />
    </Stack>
  );
}
