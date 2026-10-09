import { useState } from 'react';
import {
  Alert, Anchor, Badge, Button, Card, Divider, Group, Stack, Table, Text, Title, Tooltip,
} from '@mantine/core';
import { MonthPickerInput } from '@mantine/dates';
import { IconAlertTriangle, IconEdit, IconLogin2, IconLogout2 } from '@tabler/icons-react';
import { useAuth } from '@/auth/AuthContext';
import { Link } from 'react-router-dom';
import { useWorkDuration, useClockIn, useClockOut } from '@/hooks/useCommute';
import { useCreateCorrection } from '@/hooks/useCommuteCorrections';
import { CorrectionRequestModal, type CorrectionDraft } from '@/components/correction/CorrectionRequestModal';
import { TableStateRow } from '@/components/TableStateRow';
import { ZonedTimeText } from '@/components/ZonedTimeText';
import {
  currentYearMonth, toYearMonth, fromYearMonth, formatMinutes, formatZonedTime,
} from '@/lib/month';
import { correctionFieldErrors } from '@/lib/errorMessages';
import { notifyError, notifySuccess } from '@/lib/notify';
import type { schemas } from '@/api/types';

type CommuteDetail = schemas['CommuteDetail'];
type RegularEndTarget = schemas['RegularEndTarget'];

const DASH = <Text c="dimmed" span>—</Text>;

const LOCK_LABELS: Record<string, { label: string; color: string; hint: string }> = {
  MONTH_CLOSED: { label: '마감', color: 'gray', hint: '마감된 보고서의 집계 기간이라 변경할 수 없습니다.' },
  DELIVERY_UNCERTAIN: { label: '수신 확인 대기', color: 'orange', hint: '보고서 수신 확인 전이라 임시로 변경할 수 없습니다.' },
};

// "오늘인가"·"24시간 경과" 판정은 서버가 기록의 workZone 으로 끝내서 status 로 내려준다 —
// 브라우저 timezone 이 직원 timezone 과 다를 때 유추하면 틀린다.
function CheckOutCell({ detail, isEndTarget }: { detail: CommuteDetail; isEndTarget: boolean }) {
  switch (detail.status) {
    case 'IN_PROGRESS':
      return isEndTarget
        ? <Badge color="blue" variant="light">근무 중 · 퇴근 대상</Badge>
        : <Text c="dimmed" span>근무 중</Text>;
    case 'CORRECTION_REQUIRED':
      return (
        <Tooltip label="출근 후 24시간이 지났거나 이후 근무가 있어 일반 퇴근할 수 없습니다. 정정 신청으로 등록하세요." multiline w={280}>
          <Badge color="orange" variant="light">정정 필요</Badge>
        </Tooltip>
      );
    case 'DAY_OFF':
      return DASH;
    default:
      return <ZonedTimeText iso={detail.workEndTime} workDate={detail.date} />;
  }
}

function MinutesCell({ detail }: { detail: CommuteDetail }) {
  if (detail.usingDayOff) return DASH;
  // 미퇴근의 0분은 "근무하지 않음"의 확정값이 아니다
  if (!detail.workEndTime) return <Text c="dimmed" span>미확정</Text>;
  return <>{formatMinutes(detail.workingMinutes)}</>;
}

function NoteCell({ detail }: { detail: CommuteDetail }) {
  const lock = detail.lockReason ? LOCK_LABELS[detail.lockReason] : null;
  return (
    <Group gap={4}>
      {detail.usingDayOff && <Badge color="grape" variant="light">연차</Badge>}
      {detail.pendingCorrectionRequestId && (
        <Badge color="yellow" variant="light" component={Link} to="/me/corrections" style={{ cursor: 'pointer' }}>
          정정 대기
        </Badge>
      )}
      {lock && (
        <Tooltip label={lock.hint} multiline w={260}>
          <Badge color={lock.color} variant="outline">{lock.label}</Badge>
        </Tooltip>
      )}
    </Group>
  );
}

function RegularEndTargetText({ target, loading }: { target: RegularEndTarget | null; loading: boolean }) {
  if (loading) return null;
  if (!target) return <Text size="sm" c="dimmed">퇴근할 근무가 없습니다.</Text>;
  return (
    <Text size="sm" c="dimmed">
      퇴근 대상: {target.workDate} 출근 <ZonedTimeText iso={target.workStartTime} workDate={target.workDate} />
      {' · '}일반 퇴근 기한 {target.endableUntil.slice(0, 10)} <ZonedTimeText iso={target.endableUntil} workDate={target.endableUntil.slice(0, 10)} />
      {' '}({target.workZone})
    </Text>
  );
}

// 정정 신청은 미퇴근이 확정된 기록(CORRECTION_REQUIRED)과 완료 기록(COMPLETED)에서만 연다.
// 진행 중(IN_PROGRESS) 기록은 일반 퇴근으로 처리한다.
function correctionBlockedReason(d: CommuteDetail): string | null {
  if (d.usingDayOff || !d.workStartTime) return '연차 기록은 정정할 수 없습니다.';
  if (d.status !== 'CORRECTION_REQUIRED' && d.status !== 'COMPLETED') return '근무 중인 기록은 퇴근 버튼으로 처리하세요.';
  if (d.lockReason) return LOCK_LABELS[d.lockReason]?.hint ?? '변경할 수 없는 기록입니다.';
  if (d.pendingCorrectionRequestId) return '승인 대기 중인 요청이 있습니다. 바꾸려면 취소 후 다시 신청하세요.';
  return null;
}

export function MyCommutePage() {
  const [ym, setYm] = useState(currentYearMonth());
  const { data, isLoading, error, refetch } = useWorkDuration(ym);
  const clockIn = useClockIn();
  const clockOut = useClockOut();
  const createCorrection = useCreateCorrection();
  // 409 재조회 뒤 최신 version 으로 신청하도록 ID 만 들고 기록은 조회 결과에서 다시 찾는다
  const [targetId, setTargetId] = useState<number | null>(null);
  const target = data?.details.find((d) => d.commuteHistoryId === targetId) ?? null;
  // 일반 퇴근 대상은 서버가 정한다(조회 월과 무관 — 월 경계 야간근무 포함). details 로 추측하지 않는다.
  const endTarget = data?.regularEndTarget ?? null;
  const canClockOut = !!endTarget && !endTarget.lockReason;
  // 매니저·상위 승인자는 지정 담당자만 정정을 처리한다(멤버는 모든 매니저가 처리)
  const { user } = useAuth();
  const approverMissing = !!user && user.role !== 'MEMBER' && !user.correctionApprover;

  async function onClockIn() {
    try { await clockIn.mutateAsync(); notifySuccess('출근을 기록했습니다.'); }
    catch (e) { notifyError(e); }
  }
  async function onClockOut() {
    try { await clockOut.mutateAsync(); notifySuccess('퇴근을 기록했습니다.'); }
    catch (e) { notifyError(e); }
  }

  async function onSubmitCorrection(draft: CorrectionDraft) {
    if (!target) return;
    try {
      await createCorrection.mutateAsync({
        commuteHistoryId: target.commuteHistoryId,
        commuteVersion: target.version,
        requestedWorkEndTime: draft.requestedEndTime,
        reason: draft.reason,
      });
      notifySuccess('정정을 신청했습니다. 승인되면 근태에 반영됩니다.');
      setTargetId(null);
    } catch (e) {
      // 시각·사유 오류는 입력 칸에, 그 외는 알림으로 보여준다
      if (!correctionFieldErrors(e)) notifyError(e);
      throw e;
    }
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={3}>내 근태</Title>
        <Anchor component={Link} to="/me/corrections" size="sm">내 정정 요청 보기</Anchor>
      </Group>

      <Card withBorder>
        <Group justify="space-between" align="flex-start">
          <Stack gap={4}>
            <Text fw={500}>오늘 근무 기록</Text>
            <RegularEndTargetText target={endTarget} loading={isLoading} />
          </Stack>
          <Group>
            <Button leftSection={<IconLogin2 size={16} />} onClick={onClockIn} loading={clockIn.isPending}>출근</Button>
            <Tooltip label={endTarget?.lockReason ? LOCK_LABELS[endTarget.lockReason]?.hint : '퇴근할 근무가 없습니다.'} disabled={canClockOut} multiline w={260}>
              <Button
                leftSection={<IconLogout2 size={16} />}
                variant="light"
                onClick={onClockOut}
                loading={clockOut.isPending}
                disabled={!canClockOut}
              >
                퇴근
              </Button>
            </Tooltip>
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
          <Text c="dimmed">이 달 총 근무 (퇴근 기록된 근무 기준)</Text>
          <Text fw={700}>{data ? formatMinutes(data.sumWorkingMinutes) : '—'}</Text>
        </Group>
        <Table striped>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>날짜</Table.Th>
              <Table.Th>출근</Table.Th>
              <Table.Th>퇴근</Table.Th>
              <Table.Th>근무 시간</Table.Th>
              <Table.Th>비고</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {data?.details.map((d) => {
              const blocked = correctionBlockedReason(d);
              const showButton = !d.usingDayOff && (d.status === 'CORRECTION_REQUIRED' || d.status === 'COMPLETED');
              return (
                <Table.Tr key={d.commuteHistoryId}>
                  <Table.Td>{d.date}</Table.Td>
                  <Table.Td>{formatZonedTime(d.workStartTime) ?? DASH}</Table.Td>
                  <Table.Td><CheckOutCell detail={d} isEndTarget={d.commuteHistoryId === endTarget?.commuteHistoryId} /></Table.Td>
                  <Table.Td><MinutesCell detail={d} /></Table.Td>
                  <Table.Td><NoteCell detail={d} /></Table.Td>
                  <Table.Td ta="right">
                    {showButton && (
                      <Tooltip label={blocked} disabled={!blocked} multiline w={260}>
                        <Button
                          size="xs"
                          variant={d.status === 'CORRECTION_REQUIRED' ? 'filled' : 'subtle'}
                          color={d.status === 'CORRECTION_REQUIRED' ? 'orange' : undefined}
                          leftSection={<IconEdit size={14} />}
                          disabled={!!blocked}
                          onClick={() => setTargetId(d.commuteHistoryId)}
                        >
                          {d.status === 'CORRECTION_REQUIRED' ? '퇴근 등록 신청' : '정정 신청'}
                        </Button>
                      </Tooltip>
                    )}
                  </Table.Td>
                </Table.Tr>
              );
            })}
            <TableStateRow
              colSpan={6}
              isLoading={isLoading}
              error={error}
              isEmpty={data?.details.length === 0}
              emptyText="이 달 기록이 없습니다."
              onRetry={() => refetch()}
            />
          </Table.Tbody>
        </Table>
      </Card>

      <CorrectionRequestModal
        target={target && target.workStartTime ? {
          workDate: target.date,
          workZone: target.workZone,
          workStartTime: target.workStartTime,
          workEndTime: target.workEndTime ?? null,
        } : null}
        onClose={() => setTargetId(null)}
        onSubmit={onSubmitCorrection}
        mapError={correctionFieldErrors}
        submitting={createCorrection.isPending}
        notice={approverMissing && (
          <Alert color="orange" variant="light" icon={<IconAlertTriangle size={16} />}>
            지정된 승인 담당자가 없습니다. 신청은 되지만 담당자가 지정될 때까지 처리되지 않으며,
            대기 중에는 담당자를 지정할 수 없습니다. 근태 관리자에게 담당자 지정을 먼저 요청하세요.
          </Alert>
        )}
      />
    </Stack>
  );
}
