import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { unwrap } from '@/lib/errors';
import { isStaleConflict } from '@/lib/errorMessages';
import type { schemas } from '@/api/types';

type CorrectionStatus = schemas['CorrectionStatus'];

const KEY = ['commute-corrections'];

// 승인은 근태 원본(종료 시각·근무 분)과 마감 가능 여부를 바꾼다. 인접 월도 영향을 받으므로 근태는 월 구분 없이 무효화한다.
export function invalidateCorrectionRelated(qc: QueryClient) {
  return Promise.all([
    qc.invalidateQueries({ queryKey: KEY }),
    qc.invalidateQueries({ queryKey: ['commute'] }),
    qc.invalidateQueries({ queryKey: ['monthly-closings'] }),
  ]);
}

function onConflictRefetch(qc: QueryClient) {
  return (err: unknown) => {
    if (isStaleConflict(err)) void invalidateCorrectionRelated(qc);
  };
}

export function useMyCorrections(status?: CorrectionStatus) {
  return useQuery({
    queryKey: [...KEY, 'mine', status ?? 'ALL'],
    queryFn: async () =>
      unwrap(await api.GET('/api/commute-corrections/mine', { params: { query: { status } } })),
  });
}

export function useReviewCorrections(status?: CorrectionStatus) {
  return useQuery({
    queryKey: [...KEY, 'review', status ?? 'ALL'],
    queryFn: async () =>
      unwrap(await api.GET('/api/commute-corrections/review', { params: { query: { status } } })),
  });
}

export function useCreateCorrection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: schemas['CorrectionCreateRequest']) =>
      unwrap(await api.POST('/api/commute-corrections', { body })),
    onSuccess: () => invalidateCorrectionRelated(qc),
    onError: onConflictRefetch(qc),
  });
}

export function useCancelCorrection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (requestId: number) =>
      unwrap(await api.POST('/api/commute-corrections/{requestId}/cancel', {
        params: { path: { requestId } },
      })),
    onSuccess: () => invalidateCorrectionRelated(qc),
    onError: onConflictRefetch(qc),
  });
}

export function useApproveCorrection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { requestId: number; comment: string | null }) =>
      unwrap(await api.POST('/api/commute-corrections/{requestId}/approve', {
        params: { path: { requestId: vars.requestId } },
        body: { comment: vars.comment },
      })),
    onSuccess: () => invalidateCorrectionRelated(qc),
    onError: onConflictRefetch(qc),
  });
}

export function useRejectCorrection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { requestId: number; reason: string }) =>
      unwrap(await api.POST('/api/commute-corrections/{requestId}/reject', {
        params: { path: { requestId: vars.requestId } },
        body: { reason: vars.reason },
      })),
    onSuccess: () => invalidateCorrectionRelated(qc),
    onError: onConflictRefetch(qc),
  });
}
