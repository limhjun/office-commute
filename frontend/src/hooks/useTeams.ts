import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/lib/errors';
import type { schemas } from '@/api/types';

const KEY = ['teams'];

export function useTeams() {
  return useQuery({
    queryKey: KEY,
    queryFn: async () => unwrap(await api.GET('/team', {})),
  });
}

export function useCreateTeam() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: schemas['TeamRegisterRequest']) =>
      unwrap(await api.POST('/team', { body })),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
