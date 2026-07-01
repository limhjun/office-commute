import { createTheme } from '@mantine/core';

// 사내 백오피스: 화려함보다 가독성/밀도. Mantine 기본을 존중하되
// 데이터 테이블이 많은 화면에 맞게 기본 반경만 살짝 조인다.
export const theme = createTheme({
  primaryColor: 'indigo',
  defaultRadius: 'md',
  fontFamily:
    '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Noto Sans KR", sans-serif',
});
