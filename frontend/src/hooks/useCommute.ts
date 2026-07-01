import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/lib/errors';

export function useWorkDuration(yearMonth: string) {
  return useQuery({
    queryKey: ['commute', yearMonth],
    queryFn: async () =>
      unwrap(await api.GET('/commute', { params: { query: { yearMonth } } })),
  });
}

export function useClockIn() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => unwrap(await api.POST('/commute', {})),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['commute'] }),
  });
}

export function useClockOut() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => unwrap(await api.PUT('/commute', {})),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['commute'] }),
  });
}
