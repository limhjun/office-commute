import { Alert, Button, Group, Modal, Stack, Table, Text, Textarea } from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconInfoCircle } from '@tabler/icons-react';
import { ZonedDateTimeInput } from '@/components/ZonedDateTimeInput';
import { ZonedTimeText } from '@/components/ZonedTimeText';
import { formatMinutes } from '@/lib/month';
import {
  emptyZonedValue, pickInstant, todayInZone, zonedValueFromIso, type ZonedDateTimeValue,
} from '@/lib/zonedTime';

export interface CorrectionTarget {
  workDate: string;
  workZone: string;
  workStartTime: string;
  workEndTime: string | null;
}

export interface CorrectionDraft {
  // 기록 workZone 오프셋이 붙은 ISO-8601
  requestedEndTime: string;
  reason: string;
}

export type CorrectionFieldErrors = Partial<Record<'endTime' | 'reason', string>>;

interface Props {
  target: CorrectionTarget | null;
  onClose: () => void;
  onSubmit: (draft: CorrectionDraft) => Promise<void>;
  // 서버 오류를 입력 칸 오류로 옮길 수 있으면 반환한다. null 이면 호출부가 알림으로 처리한 것.
  mapError?: (e: unknown) => CorrectionFieldErrors | null;
  submitting: boolean;
}

export function CorrectionRequestModal({ target, onClose, ...rest }: Props) {
  return (
    <Modal
      opened={!!target}
      onClose={onClose}
      title={target?.workEndTime ? '퇴근 시각 정정 신청' : '누락된 퇴근 시각 등록 신청'}
      centered
      size="lg"
    >
      {target && (
        <CorrectionForm
          key={`${target.workDate}|${target.workEndTime ?? ''}`}
          target={target}
          onClose={onClose}
          {...rest}
        />
      )}
    </Modal>
  );
}

interface FormValues { endTime: ZonedDateTimeValue; reason: string }

// 서버와 같은 분 미만 절삭. 미리보기 전용이며 저장 값은 서버가 계산한다.
function previewMinutes(startIso: string, endMs: number): number {
  return Math.floor((endMs - Date.parse(startIso)) / 60_000);
}

function CorrectionForm({ target, onClose, onSubmit, mapError, submitting }: Props & { target: CorrectionTarget }) {
  const zone = target.workZone;
  const form = useForm<FormValues>({
    initialValues: {
      endTime: target.workEndTime ? zonedValueFromIso(target.workEndTime) : emptyZonedValue(target.workDate),
      reason: '',
    },
    validate: {
      endTime: (v) => {
        const { resolution, instant } = pickInstant(v, zone);
        if (resolution.kind === 'incomplete') return '퇴근 날짜와 시각을 입력하세요.';
        if (resolution.kind === 'invalid') return '날짜·시각 형식을 확인하세요.';
        if (resolution.kind === 'nonexistent') return '서머타임 전환으로 존재하지 않는 시각입니다.';
        if (!instant) return null;
        if (instant.epochMs < Date.parse(target.workStartTime)) return '출근 시각 이후여야 합니다.';
        if (instant.epochMs > Date.now()) return '현재 시각 이후로는 신청할 수 없습니다.';
        if (target.workEndTime && instant.epochMs === Date.parse(target.workEndTime)) return '현재 퇴근 시각과 같습니다.';
        return null;
      },
      reason: (v) => (v.trim() ? null : '정정 사유를 입력하세요.'),
    },
  });

  const { instant } = pickInstant(form.values.endTime, zone);

  async function handleSubmit(values: FormValues) {
    const picked = pickInstant(values.endTime, zone).instant;
    if (!picked) return;
    try {
      await onSubmit({ requestedEndTime: picked.iso, reason: values.reason.trim() });
    } catch (e) {
      const fieldErrors = mapError?.(e);
      if (fieldErrors) form.setErrors(fieldErrors);
    }
  }

  return (
    <form onSubmit={form.onSubmit(handleSubmit)}>
      <Stack>
        <Table withRowBorders={false} verticalSpacing={4}>
          <Table.Tbody>
            <Table.Tr>
              <Table.Td w={120}><Text size="sm" c="dimmed">근무일</Text></Table.Td>
              <Table.Td>{target.workDate}</Table.Td>
            </Table.Tr>
            <Table.Tr>
              <Table.Td><Text size="sm" c="dimmed">출근</Text></Table.Td>
              <Table.Td><ZonedTimeText iso={target.workStartTime} workDate={target.workDate} /></Table.Td>
            </Table.Tr>
            <Table.Tr>
              <Table.Td><Text size="sm" c="dimmed">현재 퇴근</Text></Table.Td>
              <Table.Td>
                {target.workEndTime
                  ? <ZonedTimeText iso={target.workEndTime} workDate={target.workDate} />
                  : <Text c="orange" size="sm" span>미기록</Text>}
              </Table.Td>
            </Table.Tr>
          </Table.Tbody>
        </Table>

        <ZonedDateTimeInput
          zone={zone}
          label="실제 퇴근 날짜"
          withAsterisk
          minDate={target.workDate}
          maxDate={todayInZone(zone)}
          value={form.values.endTime}
          onChange={(v) => form.setFieldValue('endTime', v)}
          error={form.errors.endTime}
        />
        {instant && instant.epochMs >= Date.parse(target.workStartTime) && (
          <Text size="sm" c="dimmed">
            정정 후 근무 시간(예상): {formatMinutes(previewMinutes(target.workStartTime, instant.epochMs))}
          </Text>
        )}

        <Textarea
          label="정정 사유"
          withAsterisk
          autosize
          minRows={3}
          {...form.getInputProps('reason')}
        />

        <Alert color="blue" variant="light" icon={<IconInfoCircle size={16} />}>
          승인되기 전까지 근태 기록은 바뀌지 않습니다. 제출한 시각·사유는 수정할 수 없으며,
          바꾸려면 신청을 취소한 뒤 다시 신청하세요.
        </Alert>

        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>닫기</Button>
          <Button type="submit" loading={submitting}>신청</Button>
        </Group>
      </Stack>
    </form>
  );
}
