import { Group, Stack, Text } from '@mantine/core';
import { IconArrowRight } from '@tabler/icons-react';
import { ZonedTimeText } from '@/components/ZonedTimeText';
import { formatMinutes } from '@/lib/month';

interface Props {
  workDate: string;
  beforeEndTime: string | null;
  afterEndTime: string;
  beforeMinutes?: number | null;
  afterMinutes?: number | null;
}

// 퇴근 시각 변경 전후. 변경 전이 없으면 누락 등록이다.
// 근무 분은 서버가 기록한 값을 받아 그대로 보여준다 — 화면에서 재계산하지 않는다.
export function EndTimeChange({ workDate, beforeEndTime, afterEndTime, beforeMinutes, afterMinutes }: Props) {
  const hasMinutes = afterMinutes !== undefined && afterMinutes !== null;
  return (
    <Stack gap={2}>
      <Group gap={6} wrap="nowrap">
        {beforeEndTime
          ? <ZonedTimeText iso={beforeEndTime} workDate={workDate} />
          : <Text c="orange" size="sm" span>퇴근 미기록</Text>}
        <IconArrowRight size={14} />
        <ZonedTimeText iso={afterEndTime} workDate={workDate} />
      </Group>
      {hasMinutes && (
        <Text size="xs" c="dimmed">
          {beforeMinutes !== undefined && beforeMinutes !== null && beforeEndTime ? formatMinutes(beforeMinutes) : '—'}
          {' → '}
          {formatMinutes(afterMinutes)}
        </Text>
      )}
    </Stack>
  );
}
