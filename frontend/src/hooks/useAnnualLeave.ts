import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/lib/errors';

export function useRemainingLeaves() {
  return useQuery({
    queryKey: ['annual-leave'],
    queryFn: async () => unwrap(await api.GET('/annual-leave', {})),
  });
}

export function useEnrollLeaves() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (wantedDates: string[]) =>
      unwrap(await api.POST('/annual-leave', { body: { wantedDates } })),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['annual-leave'] }),
  });
}
