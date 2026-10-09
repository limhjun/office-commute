import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/lib/errors';

export function useWorkDuration(yearMonth: string) {
  return useQuery({
    queryKey: ['commute', yearMonth],
    queryFn: async () =>
      unwrap(await api.GET('/api/commute', { params: { query: { yearMonth } } })),
  });
}

export function useClockIn() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => unwrap(await api.POST('/api/commute', {})),
    // 409(이미 퇴근·중복 출근 등)도 화면 값이 낡았다는 뜻이므로 성공·실패 모두 다시 읽는다.
    onSettled: () => qc.invalidateQueries({ queryKey: ['commute'] }),
  });
}

export function useClockOut() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => unwrap(await api.PUT('/api/commute', {})),
    onSettled: () => qc.invalidateQueries({ queryKey: ['commute'] }),
  });
}
