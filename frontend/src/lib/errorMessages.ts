import { ApiError } from './errors';

// docs/COMMUTE_CORRECTION_API.md §4 의 권장 문구. 없는 코드는 서버 message 를 그대로 쓴다.
const CODE_MESSAGES: Record<string, string> = {
  COMMUTE_END_WINDOW_EXPIRED: '출근 후 24시간이 지나 일반 퇴근할 수 없습니다. 정정 신청을 해 주세요.',
  COMMUTE_ALREADY_ENDED: '최근 근무는 이미 퇴근 처리됐습니다.',
  CLOSING_PERIOD_LOCKED: '마감된 보고서에 포함된 기간이라 변경할 수 없습니다.',
  REPORT_DELIVERY_UNCERTAIN: '보고서 수신 확인 전이라 이 기간은 임시로 변경할 수 없습니다.',
  COMMUTE_NOT_FOUND: '근태 기록을 찾을 수 없습니다.',
  CORRECTION_TARGET_DAY_OFF: '연차 기록은 정정할 수 없습니다.',
  CORRECTION_END_BEFORE_START: '종료 시각은 출근 시각 이후여야 합니다.',
  CORRECTION_END_IN_FUTURE: '종료 시각은 현재 시각 이전이어야 합니다.',
  CORRECTION_NO_CHANGE: '현재 종료 시각과 같습니다.',
  CORRECTION_OVERLAPS_NEXT_WORK: '다음 근무의 출근 시각보다 늦을 수 없습니다.',
  CORRECTION_ALREADY_PENDING: '이 기록에 승인 대기 중인 요청이 있습니다.',
  CORRECTION_VERSION_CONFLICT: '기록이 변경됐습니다. 최신 내용을 확인해 주세요.',
  CORRECTION_ALREADY_PROCESSED: '이미 처리된 요청입니다.',
  CORRECTION_APPROVER_NOT_ASSIGNED: '신청자에게 지정된 승인 담당자가 없어 처리할 수 없습니다.',
  CORRECTION_SELF_APPROVAL: '본인 요청은 처리할 수 없습니다.',
  CORRECTION_REQUEST_NOT_FOUND: '요청을 찾을 수 없습니다.',
  INVALID_CORRECTION_APPROVER: '지정할 수 없는 승인 담당자입니다.',
  PENDING_CORRECTION_EXISTS: '승인 대기 중인 정정 요청을 먼저 처리해야 합니다.',
  CLOSING_MONTH_NOT_ENDED: '끝난 과거 월만 마감할 수 있습니다.',
  MONTH_ALREADY_CLOSED: '이미 마감된 월입니다.',
  CLOSING_HAS_UNRESOLVED: '미퇴근 또는 승인 대기 요청이 남아 있습니다.',
  LEGACY_RESOLUTION_REQUIRED: '기존 발송 월입니다. 정정 여부를 선택해 주세요.',
  LEGACY_RESOLUTION_NOT_APPLICABLE: '기존 발송 월이 아닙니다.',
  CLOSING_PREVIOUS_LEGACY_MONTH_PENDING: '집계 기간이 겹치는 이전 기존 발송 월을 먼저 정리해 주세요.',
  DISPATCH_NOT_FOUND: '발송 이력이 없습니다.',
  DISPATCH_NOT_UNCERTAIN: '수신 확인이 필요한 상태가 아닙니다.',
  REPORT_FILE_NOT_FOUND: '보관된 확정본이 없습니다.',
  CSRF_ORIGIN_REJECTED: '허용되지 않은 출처의 요청입니다.',
  DUPLICATE_WORK: '오늘은 이미 출근을 기록했습니다.',
};

export function messageForError(err: unknown, fallback = '문제가 발생했습니다.'): string {
  if (!(err instanceof ApiError)) return fallback;
  return CODE_MESSAGES[err.code] ?? err.message;
}

// 409 뒤에는 화면이 본 값이 낡았으므로 관련 Query 를 다시 조회한다.
export function isStaleConflict(err: unknown): boolean {
  return err instanceof ApiError && err.status === 409;
}

// 정정 시각 오류는 fieldErrorResults 없는 ErrorResult 로 온다 — 코드로 시각 입력 칸에 붙인다.
const CORRECTION_TIME_CODES = new Set([
  'CORRECTION_END_BEFORE_START',
  'CORRECTION_END_IN_FUTURE',
  'CORRECTION_NO_CHANGE',
  'CORRECTION_OVERLAPS_NEXT_WORK',
]);

export function correctionFieldErrors(err: unknown): Partial<Record<'endTime' | 'reason', string>> | null {
  if (!(err instanceof ApiError)) return null;
  if (CORRECTION_TIME_CODES.has(err.code)) return { endTime: messageForError(err) };
  // 오프셋 누락 등 시각 역직렬화 실패
  if (err.code === 'INVALID_JSON') return { endTime: '시각 형식을 확인하세요.' };
  const errors: Partial<Record<'endTime' | 'reason', string>> = {};
  if (err.fieldErrors.requestedWorkEndTime) errors.endTime = err.fieldErrors.requestedWorkEndTime;
  if (err.fieldErrors.reason) errors.reason = err.fieldErrors.reason;
  return Object.keys(errors).length > 0 ? errors : null;
}
