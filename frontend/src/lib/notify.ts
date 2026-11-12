import { notifications } from '@mantine/notifications';
import { ApiError } from './errors';
import { messageForError } from './errorMessages';

// fallback 은 ApiError 가 아닐 때(네트워크 오류 등)만 쓰인다.
// 같은 코드라도 화면마다 뜻이 다르면 codeMessages 로 그 화면의 문구를 우선한다.
export function notifyError(
  err: unknown,
  fallback = '문제가 발생했습니다.',
  codeMessages?: Record<string, string>,
) {
  const message = err instanceof ApiError
    ? codeMessages?.[err.code] ?? messageForError(err)
    : fallback;
  notifications.show({ color: 'red', title: '오류', message });
}

// 요청은 성공했지만 후속 처리가 끝나지 않은 경우(예: 마감 성공·발송 미완료)
export function notifyWarning(message: string) {
  notifications.show({ color: 'orange', title: '확인 필요', message });
}

export function notifySuccess(message: string) {
  notifications.show({ color: 'teal', title: '완료', message });
}
