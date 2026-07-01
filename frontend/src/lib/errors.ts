import type { schemas } from '@/api/types';

type ErrorResult = schemas['ErrorResult'];
type ValidationErrorResult = schemas['ValidationErrorResult'];

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, body: ErrorResult | ValidationErrorResult | undefined) {
    super(body?.message ?? messageForStatus(status));
    this.status = status;
    this.code = body?.code ?? `HTTP_${status}`;
    this.fieldErrors = {};
    if (body && 'fieldErrorResults' in body && Array.isArray(body.fieldErrorResults)) {
      for (const fe of body.fieldErrorResults) this.fieldErrors[fe.field] = fe.message;
    }
  }
}

function messageForStatus(status: number): string {
  switch (status) {
    case 401: return '로그인이 필요합니다.';
    case 403: return '접근 권한이 없습니다.';
    case 404: return '대상을 찾을 수 없습니다.';
    case 409: return '요청이 현재 상태와 충돌합니다.';
    case 503: return '외부 데이터를 불러오지 못했습니다. 잠시 후 다시 시도하세요.';
    default:  return '요청을 처리하지 못했습니다.';
  }
}

/**
 * openapi-fetch 의 { data, error, response } 결과를 받아,
 * 에러면 ApiError 를 던지고 성공이면 data 를 반환한다.
 * TanStack Query 의 queryFn/mutationFn 안에서 쓴다.
 */
export function unwrap<T>(result: {
  data?: T;
  error?: ErrorResult | ValidationErrorResult;
  response: Response;
}): T {
  if (result.error !== undefined || !result.response.ok) {
    throw new ApiError(result.response.status, result.error);
  }
  return result.data as T;
}
