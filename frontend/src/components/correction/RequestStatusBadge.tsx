import { Badge } from '@mantine/core';

// 정정 요청의 처리 상태. 근태 기록 상태(COMPLETED 등)와는 별개다.
const REQUEST_STATUS: Record<string, { label: string; color: string }> = {
  PENDING: { label: '승인 대기', color: 'yellow' },
  APPROVED: { label: '승인', color: 'teal' },
  REJECTED: { label: '반려', color: 'red' },
  CANCELLED: { label: '취소', color: 'gray' },
};

export function RequestStatusBadge({ status }: { status: string }) {
  const s = REQUEST_STATUS[status] ?? { label: status, color: 'gray' };
  return <Badge variant="light" color={s.color}>{s.label}</Badge>;
}
