import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/lib/errors';
import type { schemas } from '@/api/types';

const KEY = ['employees'];

export function useEmployees() {
  return useQuery({
    queryKey: KEY,
    queryFn: async () => unwrap(await api.GET('/employee', {})),
  });
}

export function useCreateEmployee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: schemas['EmployeeSaveRequest']) =>
      unwrap(await api.POST('/employee', { body })),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useChangeEmployeeTeam() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { employeeId: number; teamId: number | null }) =>
      unwrap(await api.PUT('/employee/{employeeId}/team', {
        params: { path: { employeeId: vars.employeeId } },
        body: { teamId: vars.teamId },
      })),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
