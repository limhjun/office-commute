import { useState } from 'react';
import { Alert, Button, Checkbox, Group, List, Modal, Radio, Stack, Text, Textarea } from '@mantine/core';
import { IconLock } from '@tabler/icons-react';
import type { schemas } from '@/api/types';

type LegacyResolution = schemas['LegacyResolution'];

export interface CloseMonthPayload {
  legacyResolution?: LegacyResolution;
  note?: string;
}

interface Props {
  yearMonth: string | null;
  // 보고서가 참조하는 실제 집계 입력 기간 (예: 2026-08-31 ~ 2026-09-30)
  protectedFrom?: string | null;
  protectedTo?: string | null;
  // 기능 도입 전 이미 발송된 월 — 정정 여부와 확인 메모가 필수다
  legacyDispatch: boolean;
  onClose: () => void;
  onConfirm: (payload: CloseMonthPayload) => Promise<void>;
  submitting: boolean;
}

function monthLabel(ym: string): string {
  const [y, m] = ym.split('-').map(Number);
  return `${y}년 ${m}월`;
}

// 월 마감은 되돌릴 수 없으므로(마감 해제 없음) 결과를 명시하고 한 번 더 확인받는다.
export function CloseMonthConfirmModal({ yearMonth, ...rest }: Props) {
  return (
    <Modal opened={!!yearMonth} onClose={rest.onClose} title={yearMonth ? `${monthLabel(yearMonth)} 마감` : ''} centered size="lg">
      {yearMonth && <ConfirmBody key={yearMonth} {...rest} />}
    </Modal>
  );
}

function ConfirmBody({ protectedFrom, protectedTo, legacyDispatch, onClose, onConfirm, submitting }: Omit<Props, 'yearMonth'>) {
  const [acknowledged, setAcknowledged] = useState(false);
  const [resolution, setResolution] = useState<LegacyResolution | null>(null);
  const [note, setNote] = useState('');
  const [noteError, setNoteError] = useState<string | null>(null);

  const legacyReady = !legacyDispatch || resolution !== null;

  function submit() {
    if (legacyDispatch && !note.trim()) {
      setNoteError('기존 발송 월은 확인 내용을 메모로 남겨야 합니다.');
      return;
    }
    void onConfirm({
      legacyResolution: legacyDispatch && resolution ? resolution : undefined,
      note: note.trim() || undefined,
    });
  }

  return (
    <Stack>
      <Alert color="orange" variant="light" icon={<IconLock size={16} />}>
        마감은 해제할 수 없습니다.
      </Alert>
      {/* Mantine List.Item 은 nowrap + inline-flex 라 긴 문장이 불릿 폭만큼 모달 밖으로 밀린다 */}
      <List size="sm" spacing={4} styles={{ itemWrapper: { display: 'inline' } }}>
        <List.Item>
          {protectedFrom && protectedTo
            ? <>보고서 집계 기간 <Text span fw={600}>{protectedFrom} ~ {protectedTo}</Text>의 근태가 잠깁니다.</>
            : '보고서 집계 기간의 근태가 잠깁니다.'}
          {' '}전월 말 기록도 포함될 수 있습니다.
        </List.Item>
        <List.Item>마감 후에는 관리자도 이 기간의 근태를 정정·추가·삭제할 수 없습니다.</List.Item>
        {legacyDispatch
          ? <List.Item>이 월은 기능 도입 전에 보고서가 이미 발송됐습니다. 정정 여부에 따라 잠그기만 하거나 정정본을 별도로 발송합니다.</List.Item>
          : <List.Item>마감 직후 최종 보고서를 보관하고 발송합니다. 발송이 실패해도 마감은 유지되며 재시도할 수 있습니다.</List.Item>}
      </List>

      {legacyDispatch && (
        <Radio.Group
          label="기존 발송 보고서의 정정 여부"
          withAsterisk
          value={resolution}
          onChange={(v) => setResolution(v as LegacyResolution)}
        >
          <Stack gap={6} mt={6}>
            <Radio value="NO_CORRECTION_NEEDED" label="정정 없음 — 기존 발송 완료 월로 등록하고 잠급니다. 재발송하지 않습니다." />
            <Radio value="CORRECTED" label="정정 완료 — 잠근 뒤 원본과 구분된 정정본을 발송합니다." />
          </Stack>
        </Radio.Group>
      )}

      <Textarea
        label={legacyDispatch ? '확인 메모' : '메모 (선택)'}
        description={legacyDispatch ? '확인한 사실, 원본 파일 확보 여부 등' : undefined}
        withAsterisk={legacyDispatch}
        autosize
        minRows={2}
        maxLength={1000}
        value={note}
        onChange={(e) => { setNote(e.currentTarget.value); setNoteError(null); }}
        error={noteError}
      />

      <Checkbox
        label="위 내용을 확인했습니다."
        checked={acknowledged}
        onChange={(e) => setAcknowledged(e.currentTarget.checked)}
      />
      <Group justify="flex-end">
        <Button variant="default" onClick={onClose}>닫기</Button>
        <Button color="orange" leftSection={<IconLock size={16} />} disabled={!acknowledged || !legacyReady} loading={submitting} onClick={submit}>
          마감
        </Button>
      </Group>
    </Stack>
  );
}
