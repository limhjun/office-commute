import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/lib/errors';
import type { schemas } from '@/api/types';

const KEY = ['employees'];

export function useEmployees() {
  return useQuery({
    queryKey: KEY,
    queryFn: async () => unwrap(await api.GET('/api/employee', {})),
  });
}

export function useCreateEmployee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: schemas['EmployeeSaveRequest']) =>
      unwrap(await api.POST('/api/employee', { body })),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useAssignCorrectionApprover() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { employeeId: number; approverId: number | null }) =>
      unwrap(await api.PUT('/api/employee/{employeeId}/correction-approver', {
        params: { path: { employeeId: vars.employeeId } },
        body: { approverId: vars.approverId },
      })),
    // 본인의 지정 승인자가 바뀌었을 수 있으므로 /me 도 다시 읽는다
    onSuccess: () => Promise.all([
      qc.invalidateQueries({ queryKey: KEY }),
      qc.invalidateQueries({ queryKey: ['auth', 'me'] }),
    ]),
  });
}

export function useChangeEmployeeTeam() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { employeeId: number; teamId: number | null }) =>
      unwrap(await api.PUT('/api/employee/{employeeId}/team', {
        params: { path: { employeeId: vars.employeeId } },
        body: { teamId: vars.teamId },
      })),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
