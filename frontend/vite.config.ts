import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// 백엔드가 세션 쿠키(JSESSIONID) 기반이므로, 개발 중에도 브라우저 입장에서
// same-origin 이 되도록 API 경로를 모두 8080 으로 프록시한다.
// 이렇게 하면 CORS / SameSite 쿠키 설정을 건드릴 필요가 없다.
const API_PATHS = ['/api', '/team', '/employee', '/commute', '/annual-leave', '/overtime'];

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5173,
    proxy: Object.fromEntries(
      API_PATHS.map((p) => [p, { target: 'http://localhost:8080', changeOrigin: true }]),
    ),
  },
});
