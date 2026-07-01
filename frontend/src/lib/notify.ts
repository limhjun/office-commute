import { notifications } from '@mantine/notifications';
import { ApiError } from './errors';

export function notifyError(err: unknown, fallback = '문제가 발생했습니다.') {
  const message = err instanceof ApiError ? err.message : fallback;
  notifications.show({ color: 'red', title: '오류', message });
}

export function notifySuccess(message: string) {
  notifications.show({ color: 'teal', title: '완료', message });
}
