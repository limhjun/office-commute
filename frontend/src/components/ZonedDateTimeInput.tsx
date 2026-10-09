import { Group, SegmentedControl, Stack, Text } from '@mantine/core';
import { DateInput, TimeInput } from '@mantine/dates';
import { formatOffset, pickInstant, type ZonedDateTimeValue } from '@/lib/zonedTime';

// DateInput 의 Date 는 브라우저 로컬 자정이다. 연·월·일만 벽시계 날짜로 쓴다.
function toDate(date: string | null): Date | null {
  if (!date) return null;
  const [y, m, d] = date.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function fromDate(d: Date | null): string | null {
  if (!d) return null;
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

interface Props {
  zone: string;
  label: string;
  value: ZonedDateTimeValue;
  onChange: (value: ZonedDateTimeValue) => void;
  error?: React.ReactNode;
  minDate?: string;
  maxDate?: string;
  withAsterisk?: boolean;
}

/**
 * 기록의 workZone 벽시계로 날짜·시각을 입력받는다. 브라우저 timezone 으로 해석하지 않는다.
 * 같은 시각이 두 번 있는 DST 구간에서는 오프셋을 직접 고르게 하고,
 * 존재하지 않는 시각은 검증 단계(pickInstant)에서 오류로 막는다.
 */
export function ZonedDateTimeInput({ zone, label, value, onChange, error, minDate, maxDate, withAsterisk }: Props) {
  const { resolution, instant } = pickInstant(value, zone);
  const overlap = resolution.kind === 'resolved' && resolution.candidates.length === 2 ? resolution.candidates : null;

  return (
    <Stack gap={4}>
      <Group grow align="flex-start">
        <DateInput
          label={label}
          withAsterisk={withAsterisk}
          valueFormat="YYYY-MM-DD"
          value={toDate(value.date)}
          onChange={(d) => onChange({ ...value, date: fromDate(d), preferLater: false })}
          minDate={toDate(minDate ?? null) ?? undefined}
          maxDate={toDate(maxDate ?? null) ?? undefined}
          error={error}
        />
        <TimeInput
          label="시각"
          withAsterisk={withAsterisk}
          value={value.time}
          onChange={(e) => onChange({ ...value, time: e.currentTarget.value, preferLater: false })}
          error={!!error}
        />
      </Group>
      <Text size="xs" c="dimmed">
        기록 시간대 {zone}
        {instant ? ` (UTC${formatOffset(instant.offsetMinutes)}) 기준으로 입력합니다.` : ' 기준으로 입력합니다.'}
      </Text>
      {overlap && (
        <Stack gap={4}>
          <Text size="xs" c="orange">이 시각은 서머타임 전환으로 두 번 나타납니다. 실제 시각을 선택하세요.</Text>
          <SegmentedControl
            size="xs"
            value={value.preferLater ? 'later' : 'earlier'}
            onChange={(v) => onChange({ ...value, preferLater: v === 'later' })}
            data={[
              { value: 'earlier', label: `첫 번째 (UTC${formatOffset(overlap[0].offsetMinutes)})` },
              { value: 'later', label: `두 번째 (UTC${formatOffset(overlap[1].offsetMinutes)})` },
            ]}
          />
        </Stack>
      )}
    </Stack>
  );
}
