import { Button, Group, Loader, Table, Text } from '@mantine/core';
import { ApiError } from '@/lib/errors';

interface Props {
  colSpan: number;
  isLoading: boolean;
  error: unknown;
  isEmpty: boolean;
  emptyText: string;
  onRetry?: () => void;
}

// 목록 테이블의 로딩 · 오류 · 빈 상태를 한 행으로 보여준다. 데이터가 있으면 아무것도 그리지 않는다.
export function TableStateRow({ colSpan, isLoading, error, isEmpty, emptyText, onRetry }: Props) {
  let content: React.ReactNode = null;
  if (isLoading) {
    content = <Group justify="center"><Loader size="sm" /></Group>;
  } else if (error) {
    content = (
      <Group justify="center" gap="sm">
        <Text c="red" size="sm">{error instanceof ApiError ? error.message : '목록을 불러오지 못했습니다.'}</Text>
        {onRetry && <Button size="xs" variant="light" onClick={onRetry}>다시 시도</Button>}
      </Group>
    );
  } else if (isEmpty) {
    content = <Text c="dimmed" ta="center">{emptyText}</Text>;
  }
  if (!content) return null;

  return (
    <Table.Tr>
      <Table.Td colSpan={colSpan} py="lg">{content}</Table.Td>
    </Table.Tr>
  );
}
