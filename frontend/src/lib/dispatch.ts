// lastFailureReason 이 MONTH_NOT_CLOSED 로 시작하는 FAILED 는 장애가 아니라 "월 마감 전 보류"다.
export function isHeldForClosing(status: string, lastFailureReason?: string | null): boolean {
  return status === 'FAILED' && !!lastFailureReason?.startsWith('MONTH_NOT_CLOSED');
}
