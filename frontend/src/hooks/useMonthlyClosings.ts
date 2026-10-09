import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/lib/errors';
import { isStaleConflict } from '@/lib/errorMessages';
import type { schemas } from '@/api/types';

const KEY = ['monthly-closings'];

export function useMonthlyClosings() {
  return useQuery({
    queryKey: [...KEY, 'list'],
    queryFn: async () => unwrap(await api.GET('/api/monthly-closings', {})),
  });
}

export function useMonthlyClosingStatus(yearMonth: string) {
  return useQuery({
    queryKey: [...KEY, yearMonth],
    queryFn: async () =>
      unwrap(await api.GET('/api/monthly-closings/{yearMonth}', { params: { path: { yearMonth } } })),
  });
}

// 마감은 근태 잠금(lockReason)을 바꾸므로 근태 조회도 다시 읽는다.
export function useCloseMonth() {
  const qc = useQueryClient();
  const refresh = () => Promise.all([
    qc.invalidateQueries({ queryKey: KEY }),
    qc.invalidateQueries({ queryKey: ['commute'] }),
  ]);
  return useMutation({
    mutationFn: async (body: schemas['MonthlyClosingCreateRequest']) =>
      unwrap(await api.POST('/api/monthly-closings', { body })),
    onSuccess: refresh,
    onError: (err) => { if (isStaleConflict(err)) void refresh(); },
  });
}

export function useDispatchReport() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (yearMonth: string) =>
      unwrap(await api.POST('/api/overtime/report/dispatch', { params: { query: { yearMonth } } })),
    onSettled: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useConfirmDispatch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: schemas['DispatchConfirmationRequest']) =>
      unwrap(await api.POST('/api/overtime/report/dispatch/confirmation', { body })),
    // 수신 확인은 DELIVERY_UNCERTAIN 잠금을 푼다
    onSettled: () => Promise.all([
      qc.invalidateQueries({ queryKey: KEY }),
      qc.invalidateQueries({ queryKey: ['commute'] }),
    ]),
  });
}
