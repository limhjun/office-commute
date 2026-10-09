import { Badge, Tooltip } from '@mantine/core';
import { isHeldForClosing } from '@/lib/dispatch';

const DISPATCH_STATUS: Record<string, { label: string; color: string; hint: string }> = {
  IN_PROGRESS: { label: '발송 중', color: 'blue', hint: '다른 실행이 발송을 진행하고 있습니다.' },
  DELIVERY_COMMITTED: {
    label: '수신 확인 필요', color: 'orange',
    hint: '메일 발송을 시작했지만 결과가 기록되지 않았습니다. 중복 발송을 막기 위해 자동 재발송하지 않으니 수신 여부를 직접 확인하세요.',
  },
  SENT: { label: '발송 완료', color: 'teal', hint: '대표에게 발송되었습니다. 다시 발송하지 않습니다.' },
  FAILED: { label: '발송 실패', color: 'red', hint: '발송에 실패했습니다. 마감은 유지되며 재시도할 수 있습니다.' },
};

// MONTH_NOT_CLOSED 는 장애가 아니라 "마감 전이라 보류"다 — 실제 발송 실패와 구분한다.
const HELD = { label: '마감 전 보류', color: 'gray', hint: '월 마감 전이라 발송하지 않았습니다. 마감하면 발송합니다.' };

export function DispatchStatusBadge({ status, lastFailureReason }: { status: string | null; lastFailureReason?: string | null }) {
  if (!status) return <Badge variant="light" color="gray">미발송</Badge>;
  const s = isHeldForClosing(status, lastFailureReason)
    ? HELD
    : DISPATCH_STATUS[status] ?? { label: status, color: 'gray', hint: '' };
  const badge = <Badge variant="light" color={s.color}>{s.label}</Badge>;
  return s.hint ? <Tooltip label={s.hint} multiline w={280}>{badge}</Tooltip> : badge;
}

export function ReportKindBadge({ kind }: { kind: string }) {
  return kind === 'CORRECTION'
    ? <Badge variant="outline" color="grape">정정본</Badge>
    : <Badge variant="outline" color="gray">원본</Badge>;
}
