import createClient from 'openapi-fetch';
import type { paths } from './schema';

/**
 * 세션 쿠키(JSESSIONID)를 항상 실어 보낸다.
 * dev 는 Vite 프록시로 same-origin, 배포는 Spring static 으로 same-origin 이므로
 * baseUrl 은 상대경로('')로 둔다.
 */
export const api = createClient<paths>({
  baseUrl: '',
  credentials: 'include',
});
