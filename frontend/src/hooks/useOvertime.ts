import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/lib/errors';

export function useOvertime(yearMonth: string) {
  return useQuery({
    queryKey: ['overtime', yearMonth],
    queryFn: async () =>
      unwrap(await api.GET('/overtime', { params: { query: { yearMonth } } })),
  });
}
