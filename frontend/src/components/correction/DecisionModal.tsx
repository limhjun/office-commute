import { Button, Group, Modal, Stack, Textarea } from '@mantine/core';
import { useForm } from '@mantine/form';

export type Decision = 'approve' | 'reject';

interface Props {
  decision: Decision | null;
  onClose: () => void;
  // 승인 의견은 선택(null 가능), 반려 사유는 필수
  onSubmit: (comment: string | null) => Promise<void>;
  // 서버 입력 오류를 칸 오류로 돌려받는다. null 이면 호출부가 알림으로 처리한 것.
  mapError?: (e: unknown) => string | null;
  submitting: boolean;
  // 요청 요약(전후 시각·사유 등)
  children?: React.ReactNode;
}

export function DecisionModal({ decision, onClose, ...rest }: Props) {
  return (
    <Modal
      opened={!!decision}
      onClose={onClose}
      title={decision === 'reject' ? '정정 요청 반려' : '정정 요청 승인'}
      centered
      size="lg"
    >
      {decision && <DecisionForm key={decision} decision={decision} onClose={onClose} {...rest} />}
    </Modal>
  );
}

function DecisionForm({ decision, onClose, onSubmit, mapError, submitting, children }: Props & { decision: Decision }) {
  const isReject = decision === 'reject';
  const form = useForm({
    initialValues: { comment: '' },
    validate: {
      comment: (v) => (isReject && !v.trim() ? '반려 사유를 입력하세요.' : null),
    },
  });

  async function handleSubmit(values: { comment: string }) {
    try {
      await onSubmit(values.comment.trim() || null);
    } catch (e) {
      const message = mapError?.(e);
      if (message) form.setFieldError('comment', message);
    }
  }

  return (
    <form onSubmit={form.onSubmit(handleSubmit)}>
      <Stack>
        {children}
        <Textarea
          label={isReject ? '반려 사유' : '승인 의견 (선택)'}
          withAsterisk={isReject}
          autosize
          minRows={3}
          {...form.getInputProps('comment')}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>닫기</Button>
          <Button type="submit" color={isReject ? 'red' : 'teal'} loading={submitting}>
            {isReject ? '반려' : '승인'}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
