# office-commute 관리자 프론트엔드

Spring Boot 근태관리 API 위에 올리는 사내 HR 백오피스 웹앱. 저장소 루트에 `frontend/` 로 두는 것을 전제로 한다.

## 스택

- **React 18 + TypeScript + Vite** — 인증 뒤편의 내부 도구라 SSR/SEO 불필요. SPA 가 단순하고 빠르다.
- **Mantine 7** (`core` / `hooks` / `form` / `dates` / `notifications`) + Tabler Icons — 표·폼·모달·날짜 피커가 갖춰져 있어 관리자 화면을 빠르게 채운다.
- **TanStack Query 5** — 서버 상태·캐싱·로딩/에러. 401/403 은 재시도하지 않도록 설정.
- **openapi-fetch + openapi-typescript** — `openapi.yml` 을 진실의 원천으로 삼아 타입을 생성한다(백엔드의 Spec-first 를 프론트까지 확장).
- **React Router 6** — 인증/역할 기반 라우팅 가드.

## 요구사항

- Node.js 22 (CI 와 같은 버전 — `.github/workflows/ci.yml`)
- pnpm 10 (패키지 매니저 — `pnpm-lock.yaml` 이 커밋되어 있으므로 npm/yarn 을 쓰지 않는다)
- 백엔드가 `http://localhost:8080` 에서 실행 중 (`./gradlew bootRun --args='--spring.profiles.active=dev'`)

## 개발 실행

```bash
cd frontend
pnpm install
pnpm gen:api   # ../openapi.yml → src/api/schema.d.ts 재생성
pnpm dev       # http://localhost:5173
```

Vite dev 서버가 `/api` 를 `localhost:8080` 으로 프록시한다(백엔드 API 는 전부 `/api/**` 아래). 브라우저 입장에서 same-origin 이므로 **세션 쿠키(JSESSIONID)가 그대로 붙고 CORS 설정이 필요 없다.**

## 배포 (same-origin 권장)

프론트와 API 를 한 출처에서 서빙하면 세션 쿠키가 별도 설정 없이 동작한다.

```bash
pnpm build        # → frontend/dist
```

운영에서는 Nginx 가 `dist/` 를 정적으로 서빙하고 `/api` 만 백엔드로 넘긴다(`deploy/nginx.conf`, `docs/DEPLOYMENT.md`). 이렇게 하면 CORS/SameSite 를 건드릴 필요가 없다. (프론트를 별도 도메인에 올리면 백엔드에 CORS + `credentials` + 쿠키 `SameSite=None; Secure` 설정을 추가해야 한다.)

## `openapi.yml` 이 바뀌면

```bash
pnpm gen:api
```

DTO/엔드포인트가 바뀌면 타입이 갱신되고, 어긋난 호출부는 컴파일 단계에서 드러난다.

## 세션 복원: `GET /api/auth/me`

로그인 응답에는 역할(role) 본문이 없으므로, 프론트는 부팅 시 `GET /api/auth/me` 를 호출해 로그인 상태와 역할을 복원한다(`src/auth/AuthContext.tsx`). 이 엔드포인트는 백엔드 `AuthController` 에 구현되어 있고 `openapi.yml` 에도 반영되어 있으며, 비로그인 시 401 을 반환한다.

## 폴더 구조

```
src/
  api/        openapi-fetch 클라이언트 + 생성된 타입(schema.d.ts)
  auth/       AuthProvider (/api/auth/me 로 세션 복원)
  routes/     RequireAuth / RequireRole / RequireManager 가드
  components/ AppShell 레이아웃, 표 상태 행, 정정·마감 모달
  hooks/      리소스별 TanStack Query 훅
  lib/        에러 매핑(ApiError), Excel 다운로드, 알림, 월·시간대 유틸
  pages/      로그인 / 팀 / 직원 / 초과근무 / 월 마감 / 정정 승인 / 내 근태 / 내 정정 요청 / 내 연차
```

## 구현해 둔 것

- 세션 쿠키 인증 + 비로그인 시 로그인 리다이렉트(부팅 시 `/api/auth/me`), 403 시 역할 재조회
- 역할별 내비게이션·라우팅 (매니저: 팀/직원/초과근무/월 마감, 매니저·상위 승인자: 정정 승인, 전 역할: 내 근태/정정 요청/연차)
- 백엔드 에러 계약 매핑: `ErrorResult` / `ValidationErrorResult.fieldErrorResults` → 폼 필드 에러
- 도메인 에러 코드 → 사용자 문구 (`TEAM_ALREADY_EXISTS`, `ANNUAL_LEAVE_*`, `HOLIDAY_DATA_UNAVAILABLE` 등)
- Excel 리포트 blob 다운로드 (`Content-Disposition` 파일명 처리 포함)
- 초과근무 503(공휴일 데이터 미가용) 재시도 UI
