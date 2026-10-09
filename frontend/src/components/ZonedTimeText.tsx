import { Text } from '@mantine/core';
import { formatZonedTime, zonedDatePart } from '@/lib/month';

function daysBetween(fromDate: string, toDate: string): number {
  const [y1, m1, d1] = fromDate.split('-').map(Number);
  const [y2, m2, d2] = toDate.split('-').map(Number);
  return Math.round((Date.UTC(y2, m2 - 1, d2) - Date.UTC(y1, m1 - 1, d1)) / 86_400_000);
}

// 기록 오프셋 그대로 HH:mm 을 보여주고, 근무일과 날짜가 다르면 (익일)/(+N일)을 붙인다.
// 자정을 넘긴 근무는 시각만 보이면 8h가 -16h로 읽힌다.
export function ZonedTimeText({ iso, workDate }: { iso: string | null | undefined; workDate: string }) {
  const time = formatZonedTime(iso);
  if (!time) return <Text c="dimmed" span>—</Text>;
  const datePart = zonedDatePart(iso);
  const diff = datePart ? daysBetween(workDate, datePart) : 0;
  return (
    <Text span>
      {time}
      {diff !== 0 && <Text c="dimmed" span> ({diff === 1 ? '익일' : `${diff > 0 ? '+' : ''}${diff}일`})</Text>}
    </Text>
  );
}
