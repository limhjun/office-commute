import { useState } from 'react';
import { Alert, Button, Group, Modal, Radio, Stack, Textarea } from '@mantine/core';
import type { schemas } from '@/api/types';

type Outcome = schemas['DispatchConfirmationOutcome'];

interface Props {
  target: { yearMonth: string; kind: schemas['ReportKind'] } | null;
  onClose: () => void;
  onConfirm: (outcome: Outcome, note: string) => Promise<void>;
  submitting: boolean;
}

// DELIVERY_COMMITTED(수신 여부 불명)를 운영자가 확인한 결과로 확정한다.
export function DispatchConfirmModal({ target, ...rest }: Props) {
  return (
    <Modal opened={!!target} onClose={rest.onClose} title="보고서 수신 확인" centered>
      {target && <ConfirmBody key={`${target.yearMonth}|${target.kind}`} {...rest} />}
    </Modal>
  );
}

function ConfirmBody({ onClose, onConfirm, submitting }: Omit<Props, 'target'>) {
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [note, setNote] = useState('');
  const [noteError, setNoteError] = useState<string | null>(null);

  function submit() {
    if (!note.trim()) {
      setNoteError('확인 근거를 입력하세요.');
      return;
    }
    if (outcome) void onConfirm(outcome, note.trim());
  }

  return (
    <Stack>
      <Alert color="orange" variant="light">
        메일 발송을 시작했지만 결과가 기록되지 않았습니다. 수신자에게 실제로 도착했는지 확인한 뒤 기록하세요.
      </Alert>
      <Radio.Group label="확인 결과" withAsterisk value={outcome} onChange={(v) => setOutcome(v as Outcome)}>
        <Stack gap={6} mt={6}>
          <Radio value="DELIVERED" label="수신됨" />
          <Radio value="NOT_DELIVERED" label="수신되지 않음" />
        </Stack>
      </Radio.Group>
      <Textarea
        label="확인 근거"
        withAsterisk
        autosize
        minRows={2}
        maxLength={1000}
        value={note}
        onChange={(e) => { setNote(e.currentTarget.value); setNoteError(null); }}
        error={noteError}
      />
      <Group justify="flex-end">
        <Button variant="default" onClick={onClose}>닫기</Button>
        <Button disabled={!outcome} loading={submitting} onClick={submit}>기록</Button>
      </Group>
    </Stack>
  );
}
