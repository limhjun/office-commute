import { Badge, Tooltip } from '@mantine/core';

const DISPATCH_STATUS: Record<string, { label: string; color: string; hint: string }> = {
  IN_PROGRESS: { label: '발송 중', color: 'blue', hint: '다른 실행이 발송을 진행하고 있습니다.' },
  DELIVERY_COMMITTED: {
    label: '수신 확인 필요', color: 'orange',
    hint: '메일 발송을 시작했지만 결과가 기록되지 않았습니다. 중복 발송을 막기 위해 자동 재발송하지 않으니 수신 여부를 직접 확인하세요.',
  },
  SENT: { label: '발송 완료', color: 'teal', hint: '대표에게 발송되었습니다. 다시 발송하지 않습니다.' },
  FAILED: { label: '발송 실패', color: 'red', hint: '발송에 실패했습니다. 마감은 유지되며 재시도할 수 있습니다.' },
};

export function DispatchStatusBadge({ status }: { status: string | null }) {
  if (!status) return <Badge variant="light" color="gray">미발송</Badge>;
  const s = DISPATCH_STATUS[status] ?? { label: status, color: 'gray', hint: '' };
  const badge = <Badge variant="light" color={s.color}>{s.label}</Badge>;
  return s.hint ? <Tooltip label={s.hint} multiline w={280}>{badge}</Tooltip> : badge;
}
