import { notifications } from '@mantine/notifications';
import { ApiError } from './errors';
import { messageForError } from './errorMessages';

export function notifyError(err: unknown, fallback = '문제가 발생했습니다.') {
  const message = err instanceof ApiError ? messageForError(err) : fallback;
  notifications.show({ color: 'red', title: '오류', message });
}

// 요청은 성공했지만 후속 처리가 끝나지 않은 경우(예: 마감 성공·발송 미완료)
export function notifyWarning(message: string) {
  notifications.show({ color: 'orange', title: '확인 필요', message });
}

export function notifySuccess(message: string) {
  notifications.show({ color: 'teal', title: '완료', message });
}
