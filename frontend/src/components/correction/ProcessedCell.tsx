import { Stack, Text } from '@mantine/core';
import { formatInstant } from '@/lib/month';
import type { schemas } from '@/api/types';

// 처리자·처리 시각·의견(승인 의견 또는 반려 사유). 취소는 신청자 본인이 처리자다.
export function ProcessedCell({ request }: { request: schemas['CorrectionRequestResponse'] }) {
  if (request.status === 'PENDING') {
    // 매니저·상위 승인자의 요청은 지정 담당자만 처리한다. 담당자가 없으면 누구도 처리할 수 없다.
    if (request.requesterRole !== 'MEMBER' && !request.assignedApprover) {
      return <Text size="sm" c="orange">담당자 미지정 — 처리할 수 없습니다. 취소 후 담당자 지정을 요청하세요.</Text>;
    }
    return request.assignedApprover
      ? <Text size="sm" c="dimmed">담당: {request.assignedApprover.name}</Text>
      : <Text size="sm" c="dimmed">—</Text>;
  }
  return (
    <Stack gap={2}>
      <Text size="sm">{request.processedBy?.name ?? '—'}</Text>
      <Text size="xs" c="dimmed">{formatInstant(request.processedAt)}</Text>
      {request.reviewComment && (
        <Text size="xs" c={request.status === 'REJECTED' ? 'red' : 'dimmed'} style={{ whiteSpace: 'pre-wrap' }}>
          {request.status === 'REJECTED' ? '반려 사유: ' : '의견: '}{request.reviewComment}
        </Text>
      )}
    </Stack>
  );
}
