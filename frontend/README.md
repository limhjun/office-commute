# office-commute 관리자 프론트엔드

Spring Boot 근태관리 API 위에 올리는 사내 HR 백오피스 웹앱. 저장소 루트에 `frontend/` 로 두는 것을 전제로 한다.

## 스택

- **React 18 + TypeScript + Vite** — 인증 뒤편의 내부 도구라 SSR/SEO 불필요. SPA 가 단순하고 빠르다.
- **Mantine 7** (`core` / `hooks` / `form` / `dates` / `notifications`) + Tabler Icons — 표·폼·모달·날짜 피커가 갖춰져 있어 관리자 화면을 빠르게 채운다.
- **TanStack Query 5** — 서버 상태·캐싱·로딩/에러. 401/403 은 재시도하지 않도록 설정.
- **openapi-fetch + openapi-typescript** — `openapi.yml` 을 진실의 원천으로 삼아 타입을 생성한다(백엔드의 Spec-first 를 프론트까지 확장).
- **React Router 6** — 인증/역할 기반 라우팅 가드.

## 요구사항

- Node.js 18+ (권장 20+)
- 백엔드가 `http://localhost:8080` 에서 실행 중 (`./gradlew bootRun --args='--spring.profiles.active=dev'`)

## 개발 실행

```bash
cd frontend
npm install
npm run gen:api   # ../openapi.yml → src/api/schema.d.ts 재생성
npm run dev       # http://localhost:5173
```

Vite dev 서버가 `/api`, `/team`, `/employee`, `/commute`, `/annual-leave`, `/overtime` 를 `localhost:8080` 으로 프록시한다. 브라우저 입장에서 same-origin 이므로 **세션 쿠키(JSESSIONID)가 그대로 붙고 CORS 설정이 필요 없다.**

## 배포 (same-origin 권장)

프론트를 Spring 과 한 서버에서 서빙하면 세션 쿠키가 별도 설정 없이 동작한다.

```bash
npm run build     # → frontend/dist
```

`dist/` 를 백엔드의 `src/main/resources/static/` 으로 복사하거나, Gradle 빌드에 프론트 빌드를 연결한다. 이렇게 하면 CORS/SameSite 를 건드릴 필요가 없다. (프론트를 별도 도메인에 올리면 백엔드에 CORS + `credentials` + 쿠키 `SameSite=None; Secure` 설정을 추가해야 한다.)

## `openapi.yml` 이 바뀌면

```bash
npm run gen:api
```

DTO/엔드포인트가 바뀌면 타입이 갱신되고, 어긋난 호출부는 컴파일 단계에서 드러난다.

## ⚠️ 백엔드에 추가가 필요한 엔드포인트: `GET /api/auth/me`

로그인 응답에는 역할(role) 본문이 없어서, 프론트가 매니저/멤버 화면을 나누려면 **세션의 현재 사용자·역할을 돌려주는 조회 엔드포인트**가 필요하다. 프론트는 부팅 시 이걸 호출해 로그인 상태와 역할을 복원한다(`src/auth/AuthContext.tsx`).

`/api/auth/**` 는 `WebConfig` 의 `AuthInterceptor` 에서 제외되므로, 이 컨트롤러가 세션을 직접 읽고 비로그인 시 401 을 반환하면 된다.

```java
// controller/auth/AuthController.java 에 추가
@GetMapping("/api/auth/me")
public ResponseEntity<CurrentUserResponse> me(HttpSession session) {
    Long employeeId = (Long) session.getAttribute("currentEmployeeId");
    Role role = (Role) session.getAttribute("currentRole");
    if (employeeId == null || role == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    Employee employee = employeeService.findById(employeeId); // name 등 표시용
    return ResponseEntity.ok(new CurrentUserResponse(employeeId, employee.getName(), role));
}

// dto: record CurrentUserResponse(Long employeeId, String name, Role role) {}
```

추가 후 `openapi.yml` 에도 `/api/auth/me` 를 반영하고(Spec-first) `npm run gen:api` 를 다시 돌린다. 이 엔드포인트가 없으면 앱은 항상 비로그인 상태로 간주한다.

## 폴더 구조

```
src/
  api/        openapi-fetch 클라이언트 + 생성된 타입(schema.d.ts)
  auth/       AuthProvider (/api/auth/me 로 세션 복원)
  routes/     RequireAuth / RequireManager 가드
  components/ AppShell 레이아웃
  hooks/      리소스별 TanStack Query 훅
  lib/        에러 매핑(ApiError), Excel 다운로드, 알림, 월 유틸
  pages/      로그인 / 팀 / 직원 / 내 근태 / 내 연차 / 초과근무
```

## 구현해 둔 것

- 세션 쿠키 인증 + 401 시 로그인 리다이렉트
- 역할별 내비게이션·라우팅 (매니저: 팀/직원/초과근무, 멤버: 내 근태/연차)
- 백엔드 에러 계약 매핑: `ErrorResult` / `ValidationErrorResult.fieldErrorResults` → 폼 필드 에러
- 도메인 에러 코드 → 사용자 문구 (`TEAM_ALREADY_EXISTS`, `ANNUAL_LEAVE_*`, `HOLIDAY_DATA_UNAVAILABLE` 등)
- Excel 리포트 blob 다운로드 (`Content-Disposition` 파일명 처리 포함)
- 초과근무 503(공휴일 데이터 미가용) 재시도 UI
