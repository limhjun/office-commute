import { SegmentedControl } from '@mantine/core';
import type { schemas } from '@/api/types';

export type CorrectionStatusFilterValue = schemas['CorrectionStatus'] | 'ALL';

export function CorrectionStatusFilter({ value, onChange }: {
  value: CorrectionStatusFilterValue;
  onChange: (v: CorrectionStatusFilterValue) => void;
}) {
  return (
    <SegmentedControl
      size="xs"
      value={value}
      onChange={(v) => onChange(v as CorrectionStatusFilterValue)}
      data={[
        { value: 'PENDING', label: '승인 대기' },
        { value: 'APPROVED', label: '승인' },
        { value: 'REJECTED', label: '반려' },
        { value: 'CANCELLED', label: '취소' },
        { value: 'ALL', label: '전체' },
      ]}
    />
  );
}
