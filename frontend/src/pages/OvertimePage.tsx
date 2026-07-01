import { useState } from 'react';
import { Button, Card, Group, Stack, Table, Title, Text, Alert } from '@mantine/core';
import { MonthPickerInput } from '@mantine/dates';
import { IconFileSpreadsheet, IconAlertTriangle } from '@tabler/icons-react';
import { useOvertime } from '@/hooks/useOvertime';
import { currentYearMonth, toYearMonth, fromYearMonth, formatMinutes } from '@/lib/month';
import { downloadFile } from '@/lib/download';
import { ApiError } from '@/lib/errors';
import { notifyError, notifySuccess } from '@/lib/notify';

export function OvertimePage() {
  const [ym, setYm] = useState(currentYearMonth());
  const { data, isLoading, error, refetch } = useOvertime(ym);
  const [downloading, setDownloading] = useState(false);

  const holidayUnavailable = error instanceof ApiError && error.code === 'HOLIDAY_DATA_UNAVAILABLE';

  async function onDownload() {
    setDownloading(true);
    try {
      await downloadFile(`/overtime/report/excel?yearMonth=${ym}`, `${ym}_초과근무보고서.xlsx`);
      notifySuccess('리포트를 내려받았습니다.');
    } catch (e) {
      notifyError(e, '리포트를 내려받지 못했습니다.');
    } finally {
      setDownloading(false);
    }
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={3}>초과근무 · 리포트</Title>
        <Group>
          <MonthPickerInput
            value={fromYearMonth(ym)}
            onChange={(d) => setYm(toYearMonth(d))}
            valueFormat="YYYY년 M월"
            w={160}
          />
          <Button
            leftSection={<IconFileSpreadsheet size={16} />}
            onClick={onDownload}
            loading={downloading}
            disabled={holidayUnavailable}
          >
            Excel 리포트
          </Button>
        </Group>
      </Group>

      {holidayUnavailable && (
        <Alert color="orange" icon={<IconAlertTriangle size={16} />} title="공휴일 데이터를 불러오지 못했습니다">
          <Group justify="space-between">
            <Text size="sm">외부 공휴일 API 응답이 없어 초과근무를 계산할 수 없습니다.</Text>
            <Button size="xs" variant="light" color="orange" onClick={() => refetch()}>다시 시도</Button>
          </Group>
        </Alert>
      )}

      <Card withBorder p={0}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>직원</Table.Th>
              <Table.Th>팀</Table.Th>
              <Table.Th ta="right">초과근무</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {data?.map((r) => (
              <Table.Tr key={r.id}>
                <Table.Td>{r.name}</Table.Td>
                <Table.Td>{r.teamName ?? <Text c="dimmed" span>미배정</Text>}</Table.Td>
                <Table.Td ta="right">
                  {r.overTimeMinutes > 0
                    ? formatMinutes(r.overTimeMinutes)
                    : <Text c="dimmed" span>0시간</Text>}
                </Table.Td>
              </Table.Tr>
            ))}
            {!isLoading && !holidayUnavailable && data?.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={3}>
                  <Text c="dimmed" ta="center" py="lg">해당 월의 데이터가 없습니다.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Card>
    </Stack>
  );
}
