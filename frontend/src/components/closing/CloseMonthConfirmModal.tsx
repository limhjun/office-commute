import { useState } from 'react';
import { Alert, Button, Checkbox, Group, List, Modal, Stack, Text } from '@mantine/core';
import { IconLock } from '@tabler/icons-react';

interface Props {
  yearMonth: string | null;
  // 보고서가 참조하는 실제 집계 입력 기간 (예: 2026-08-31 ~ 2026-09-30)
  protectedFrom?: string | null;
  protectedTo?: string | null;
  onClose: () => void;
  onConfirm: () => Promise<void>;
  submitting: boolean;
}

function monthLabel(ym: string): string {
  const [y, m] = ym.split('-').map(Number);
  return `${y}년 ${m}월`;
}

// 월 마감은 되돌릴 수 없으므로(마감 해제 없음) 결과를 명시하고 한 번 더 확인받는다.
export function CloseMonthConfirmModal({ yearMonth, ...rest }: Props) {
  return (
    <Modal opened={!!yearMonth} onClose={rest.onClose} title={yearMonth ? `${monthLabel(yearMonth)} 마감` : ''} centered>
      {yearMonth && <ConfirmBody key={yearMonth} {...rest} />}
    </Modal>
  );
}

function ConfirmBody({ protectedFrom, protectedTo, onClose, onConfirm, submitting }: Omit<Props, 'yearMonth'>) {
  const [acknowledged, setAcknowledged] = useState(false);
  return (
    <Stack>
      <Alert color="orange" variant="light" icon={<IconLock size={16} />}>
        마감은 해제할 수 없습니다.
      </Alert>
      <List size="sm" spacing={4}>
        <List.Item>
          {protectedFrom && protectedTo
            ? <>보고서 집계 기간 <Text span fw={600}>{protectedFrom} ~ {protectedTo}</Text>의 근태가 잠깁니다.</>
            : '보고서 집계 기간의 근태가 잠깁니다.'}
          {' '}전월 말 기록도 포함될 수 있습니다.
        </List.Item>
        <List.Item>마감 후에는 관리자도 이 기간의 근태를 정정·추가·삭제할 수 없습니다.</List.Item>
        <List.Item>마감 직후 최종 보고서를 보관하고 발송합니다. 발송이 실패해도 마감은 유지되며 재시도할 수 있습니다.</List.Item>
      </List>
      <Checkbox
        label="위 내용을 확인했습니다."
        checked={acknowledged}
        onChange={(e) => setAcknowledged(e.currentTarget.checked)}
      />
      <Group justify="flex-end">
        <Button variant="default" onClick={onClose}>닫기</Button>
        <Button color="orange" leftSection={<IconLock size={16} />} disabled={!acknowledged} loading={submitting} onClick={onConfirm}>
          마감
        </Button>
      </Group>
    </Stack>
  );
}
