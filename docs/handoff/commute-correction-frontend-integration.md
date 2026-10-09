# 인계: commute-correction — Backend → Frontend 연결

이 문서는 Frontend 세션이 정정·월 마감 화면을 Backend에 연결하는 데 필요한 내용을 모은다. 비밀번호·세션 쿠키·API 키 값은 기록하지 않는다.

## 1. 위치와 기준 커밋

| 항목 | 값 |
| --- | --- |
| Backend worktree | `/Users/hyungjun/Developer/office-commute/.claude/worktrees/backend` |
| 브랜치 | `worktree-backend` (원격 push 안 함 — 로컬 브랜치) |
| API 계약 커밋 | `fd51868` — 공유 이후 계약 변경 없음 |
| 최종 구현 커밋 | `d2c146c` (`d2c146cf2885b54d70203df03a6d4f21ab277017`) |
| 인계 커밋 | `b612e9f` 이후 문서 커밋들(코드 변경 없음) |
| 계약 파일 | worktree 루트의 `openapi.yml` |
| 생성 타입 | `frontend/src/api/schema.d.ts` (`fd51868`에 포함, `pnpm --dir frontend gen:api`로 생성. 직접 편집 금지) |
| 계약 상세 | `docs/COMMUTE_CORRECTION_API.md` (경로·요청/응답 예시·오류 코드·권장 문구·역할별 권한) |
| 운영 안내 | `docs/COMMUTE_CORRECTION_OPERATIONS.md` |

Frontend 브랜치(`worktree-frontend`, `0beea38`)에서 계약을 받는 방법은 둘 중 하나다.
- `git cherry-pick fd51868`: 계약·생성 타입·계약 문서만 받는다. 이 커밋은 `openapi.yml`, `schema.d.ts`, `docs/COMMUTE_CORRECTION_API.md`, 당시 WORKING 상태의 `docs/handoff/commute-correction-backend.md`만 바꾸고 코드는 바꾸지 않는다.
- 또는 `openapi.yml`만 가져와 Frontend 쪽에서 `pnpm --dir frontend gen:api`를 실행한다.

## 2. API 요약

전체 목록과 예시는 `docs/COMMUTE_CORRECTION_API.md` §2~§3에 있다. 화면 연결에 필요한 경로는 다음과 같다.

| 화면 | 호출 |
| --- | --- |
| 내 근태 | `GET /api/commute?yearMonth=` — `commuteHistoryId`, `version`, `workZone`, `status`, `pendingCorrectionRequestId?`, `lockReason?` |
| 정정 신청 | `POST /api/commute-corrections` `{ commuteHistoryId, commuteVersion, requestedWorkEndTime, reason }` → 201 |
| 내 요청 이력·취소 | `GET /api/commute-corrections/mine?status=`, `POST /api/commute-corrections/{id}/cancel` |
| 승인 목록·처리 | `GET /api/commute-corrections/review?status=`, `POST …/{id}/approve` `{ comment? }`, `POST …/{id}/reject` `{ reason }` |
| 단건 | `GET /api/commute-corrections/{id}` |
| 담당자 지정 | `PUT /api/employee/{id}/correction-approver` `{ approverId \| null }`, 직원 목록의 `correctionApprover` |
| 월 마감 | `GET /api/monthly-closings`, `GET /api/monthly-closings/{yearMonth}`, `POST /api/monthly-closings` `{ yearMonth, legacyResolution?, note? }` → 201 `{ closing, dispatch? }` |
| 발송 | `POST /api/overtime/report/dispatch?yearMonth=`, `POST /api/overtime/report/dispatch/confirmation`, `GET /api/overtime/report/final-excel?yearMonth=&kind=`, `GET /api/overtime/report/excel`(참고용) |

공통 규칙:
- 응답 JSON은 null 필드를 **생략**한다. 생성 타입에서는 `?:`로 나타난다.
- 시각 필드는 기록 `workZone`의 오프셋으로 내려간다. `workStartTime`에는 마이크로초가 붙을 수 있다(예: `…T23:32:40.611261+09:00`). 신청·처리 시각(`requestedAt`, `processedAt`, `closedAt`)은 UTC다.
- 정정 종료 시각은 오프셋을 반드시 포함해야 한다. 서버는 초 미만을 버린다.

## 3. 오류 처리

- 형식은 기존 `ErrorResult { code, message }` / `ValidationErrorResult { code, message, fieldErrorResults }` 그대로다.
- 정정 시각 오류(`CORRECTION_END_BEFORE_START`, `CORRECTION_END_IN_FUTURE`, `CORRECTION_NO_CHANGE`, `CORRECTION_OVERLAPS_NEXT_WORK`)는 `fieldErrorResults` 없는 `ErrorResult`다. 시각 입력 필드 오류로 보여 주려면 Frontend가 코드 → 필드로 매핑해야 한다.
- Bean Validation 오류의 필드명은 DTO 필드명이다.
  - 신청: `commuteHistoryId`, `commuteVersion`, `requestedWorkEndTime`, `reason`
  - 반려: `reason`
  - 승인: `comment`
  - 마감: `yearMonth`, `note`
  - 수신 확인: `yearMonth`, `kind`, `outcome`, `note`
- 409 `CORRECTION_VERSION_CONFLICT`·`CORRECTION_ALREADY_PROCESSED`·`CORRECTION_ALREADY_PENDING`·`CLOSING_HAS_UNRESOLVED` 뒤에는 관련 Query를 무효화하고 다시 조회한다. 대상은 근태 월(인접 월 포함), 내 요청, 처리 목록, 마감 상태다. 버전 충돌은 요청을 반려하지 않으며, 요청은 PENDING으로 남는다.
- 발송 응답 `status: FAILED`의 `lastFailureReason`이 `MONTH_NOT_CLOSED:`로 시작하면 장애가 아니라 "월 마감 전 보류"다. 실제 장애와 다르게 표시한다.
- 전체 코드와 권장 문구는 `docs/COMMUTE_CORRECTION_API.md` §4에 있다.

## 4. 세션·권한·CSRF

- 인증은 기존과 같다(`JSESSIONID`, `baseUrl: ''`, `credentials: 'include'`, Vite `/api` 프록시).
- 역할은 **매 요청 DB에서 다시 읽는다**.
  - 세션 중 역할이 바뀌면 다음 요청부터 바뀐 역할로 판정된다(403 가능).
  - 세션의 직원이 삭제됐으면 401이다.
  - `/api/auth/me`도 현재 DB 역할을 돌려준다. 403 이후 `/me`를 다시 조회하면 메뉴를 맞출 수 있다.
- `COMMUTE_APPROVER`는 `@ManagerOnly` API(직원·팀 등록, 초과근무, Excel, 발송, 월 마감, 담당자 지정)에서 403이다.
  - 사용할 수 있는 API: 본인 근태·연차, 정정 신청·이력, `/review`
  - `GET /api/team`은 기존대로 모든 인증 사용자에게 열려 있다.
- CSRF 처리:
  - GET·HEAD·OPTIONS를 뺀 `/api/**` 요청은 `Origin`(없으면 `Referer`)이 같은 출처이거나 허용 목록에 있어야 한다. 아니면 403 `CSRF_ORIGIN_REJECTED`다.
  - dev·mysql 프로파일은 `http://localhost:5173`을 허용한다(`app.security.allowed-origins`). Vite 프록시의 `changeOrigin: true`를 그대로 둬도 동작하며, Frontend 변경은 필요 없다.
  - 운영은 같은 출처라 추가 설정이 없다.
  - 다른 포트나 호스트에서 dev 서버를 띄우면 `app.security.allowed-origins`에 그 출처를 추가해야 한다.

## 5. 테스트 결과와 미검증 항목

| 검증 | 결과 |
| --- | --- |
| `./gradlew clean check --rerun-tasks` (Java 21, `d2c146c`) | 성공. 351개 실행, 실패 0, 건너뜀 6 |
| `./gradlew openApiValidate` | Spec is valid |
| 로컬 dev 서버 스모크(2026-10-09) | 아래 표 |

로컬 스모크는 `bootRun`(dev, H2)에서 curl로 실행했다.

| 시나리오 | 결과 |
| --- | --- |
| 관리자·상위 승인자 로그인, `/api/auth/me` | 200. `role`과 `correctionApprover`가 나온다 |
| 상위 승인자의 `GET /api/employee` | 403 `FORBIDDEN` |
| 관리자의 MEMBER 생성 → MEMBER 출근 → 월 조회 | `commuteHistoryId`·`version`·`workZone`·`IN_PROGRESS` |
| 출근보다 이른 시각으로 정정 신청 | 400 `CORRECTION_END_BEFORE_START` |
| 정상 정정 신청 → 관리자 `/review` | 201 PENDING, 목록에 표시 |
| `Origin: https://evil.example.com`으로 승인 | 403 `CSRF_ORIGIN_REJECTED` |
| `Origin: http://localhost:5173`으로 승인 | 200 APPROVED. 근태가 `version` 1, `COMPLETED`, 종료 시각 초 단위로 바뀜 |
| 전월 마감 상태 | 200. `rangeStart` 2026-08-31, `closable: true` |
| 진행 중인 월 마감 | 400 `CLOSING_MONTH_NOT_ENDED` |
| 로그아웃 후 요청 | 401 |

미검증 항목:
- **MySQL/Flyway**: `*MySqlIntegrationTest` 6개가 Docker 부재로 건너뛰어졌다. 통과로 보지 않는다. V15·V16 적용, `validate`, 제약, `FOR UPDATE`, DATETIME(6)을 통합 단계에서 Docker 환경으로 확인해야 한다.
- 동시성은 H2에서만 검증했다(MySQL InnoDB 미검증).
- 월 마감 직후 실제 발송: dev는 공휴일 API 키가 더미이고 수신자 설정(`report.mail.*`)이 비어 있다. 마감 후 발송은 `HOLIDAY_DATA_UNAVAILABLE` 또는 메일 설정 누락으로 FAILED가 된다. 실제 메일은 나가지 않으며, 마감 자체는 유지된다. 스모크에서 마감 POST는 실행하지 않았다.
- 브라우저 확인은 하지 않았다(Frontend 범위).

## 6. 로컬 Backend 실행

```bash
cd /Users/hyungjun/Developer/office-commute/.claude/worktrees/backend
# Java 21 필요. 시스템 기본이 다른 버전이면 JAVA_HOME 을 Java 21 로 지정한다.
# PUBLIC_API_SERVICE_KEY 가 없으면 기동이 실패한다. 공휴일 API 를 쓰지 않는 화면 확인용이면 임의 값으로 충분하다
# (초과근무·Excel·마감 후 발송은 HOLIDAY_DATA_UNAVAILABLE 이 된다). 실제 키는 .env 또는 환경변수로만 넣는다.
PUBLIC_API_SERVICE_KEY=<로컬용 값> ./gradlew bootRun --args='--spring.profiles.active=dev'
# 8080. Frontend 는 pnpm --dir frontend dev (5173, /api 프록시)
```

- dev는 H2 인메모리 DB다. 재시작하면 데이터가 초기화되고 `data.sql`이 다시 들어간다.
- 같은 8080 포트를 Frontend worktree의 Backend와 동시에 쓸 수 없다.

## 7. 역할별 테스트 계정 준비

`src/main/resources/data.sql`이 dev 기동 시 두 계정을 만든다. 비밀번호는 같은 파일의 주석(개발용 평문)을 확인한다. 이 문서에는 기록하지 않는다.

| 역할 | 이메일 | 비고 |
| --- | --- | --- |
| MANAGER | `admin@company.com` | 지정 승인자 = 상위 승인자 계정 |
| COMMUTE_APPROVER | `approver@company.com` | 지정 승인자 = 관리자 계정 |

MEMBER 계정은 시드가 없다. 관리자로 로그인한 뒤 직원 등록 화면 또는 API로 만든다.

```http
POST /api/employee
{ "name": "테스트멤버", "role": "MEMBER", "birthday": "1995-01-01", "workStartDate": "2026-01-01",
  "employeeCode": "MEMBER01", "email": "member@company.com", "password": "<8자 이상 임의 값>" }
```

추가 시나리오 준비:
- **승인자 미지정 오류** 확인: MANAGER를 하나 더 만들고 담당자를 지정하지 않은 채 정정 신청 → `CORRECTION_APPROVER_NOT_ASSIGNED`
- **담당자 변경 차단**: 관리자가 정정 대기 중일 때 `PUT /api/employee/1/correction-approver` → `PENDING_CORRECTION_EXISTS`
- **과거 기록 정정**: dev에서 출근은 현재 시각으로만 생긴다. 과거 날짜 미퇴근 기록은 API로 만들 수 없다. 화면 확인은 오늘 출근 기록으로 하거나, H2 콘솔·SQL로 직접 넣는다(dev 전용).
- **월 마감 차단**: 전월에 미퇴근 기록이 없으면 `closable: true`다. 차단 화면을 보려면 위와 같이 전월 미퇴근 기록을 준비한다.

## 8. Frontend 7개 확인 항목 대조

출처: Frontend worktree의 미커밋 인계 메모 `.handoff/commute-correction-frontend.md`의 "통합 단계에서 확인할 항목" 1~7.

| # | Frontend 요구·가정 | Backend 구현 | 판정 / 조치 |
| --- | --- | --- | --- |
| 1 | 계약 커밋 후 `gen:api`로 `schema.d.ts` 재생성, `build` 재실행 | 계약 `fd51868` 커밋 완료, `schema.d.ts` 포함 | **해소**. BLOCKED 사유가 사라졌다. 재생성 후 `MyCommutePage.tsx`의 `'UNCLOSED'` 분기가 `tsc` 오류가 되므로 `CORRECTION_REQUIRED`로 바꿔야 한다. |
| 2 | `Role` enum이 `[MANAGER, MEMBER]`, `COMMUTE_APPROVER` 추가 시 라벨·NAV·가드 정합 | `Role = [MANAGER, MEMBER, COMMUTE_APPROVER]` | **Frontend 조치 필요**. `lib/roles.ts`에 `COMMUTE_APPROVER` 라벨이 없다. 노출 정책: 관리자 화면(팀·직원·초과근무·마감)은 MANAGER만, 내 근태·연차·정정은 전 역할, 승인 화면은 MANAGER·COMMUTE_APPROVER. |
| 3 | 오류 코드 매핑(END_BEFORE_START, END_IN_FUTURE, NO_CHANGE, VERSION_CONFLICT, ALREADY_PENDING, OVERLAPS_NEXT_WORK, APPROVER_NOT_ASSIGNED, TARGET_DAY_OFF) | 8개 모두 존재. HTTP 400/409, 의미 동일 | **일치**. 시각 오류 4개는 `fieldErrorResults` 없는 `ErrorResult`라 `mapError`에서 코드 → `endTime`으로 매핑해야 한다. 추가 코드 `CORRECTION_ALREADY_PROCESSED`, `CORRECTION_SELF_APPROVAL`(403), `COMMUTE_NOT_FOUND`, `CLOSING_PERIOD_LOCKED`, `REPORT_DELIVERY_UNCERTAIN`, `INVALID_JSON`(오프셋 누락)도 처리해야 한다. |
| 4 | 모달이 `requestedEndTime`(workZone 오프셋 ISO)·`reason`을 넘김 | 계약 필드 `requestedWorkEndTime`, 필수 `commuteHistoryId`·`commuteVersion` | **불일치(이름)**. hook에서 `requestedEndTime` → `requestedWorkEndTime`으로 바꾸고, `CorrectionTarget`에 없는 `commuteHistoryId`·`version`을 `CommuteDetail`에서 함께 넘긴다. 오프셋 ISO 형식은 계약과 일치한다(서버 초 미만 절삭). 반려 사유는 서버 필드가 `reason`, `DecisionModal`의 폼 필드는 `comment`라 매핑이 필요하다. |
| 5 | `DispatchStatusBadge` `IN_PROGRESS/DELIVERY_COMMITTED/SENT/FAILED`, `RequestStatusBadge` `PENDING/APPROVED/REJECTED/CANCELLED` | `DispatchStatus`, `CorrectionStatus` enum 동일 | **일치**. 다만 `FAILED` 툴팁 "마감은 유지되며 재시도할 수 있습니다"는 `lastFailureReason`이 `MONTH_NOT_CLOSED`일 때 맞지 않는다(마감 전 보류). 발송 응답의 `kind`(ORIGINAL/CORRECTION)도 함께 표시해야 원본과 정정본이 구분된다. |
| 6 | `latestClosableYearMonth`(Asia/Seoul 직전 월)가 백엔드 규칙과 같은지 | 서버는 Asia/Seoul 기준 `yearMonth < 이번 달`이면 마감 대상(직전 월만이 아님) | **부분 일치**. 직전 월 기본값은 같다. 다만 서버는 더 이전 월도 허용하며, 기존 발송 월 전환에는 이전 월 마감이 필요하다. 마감 가능 여부·보호 기간은 `GET /api/monthly-closings/{ym}`의 `closable`·`blockers`·`rangeStart`·`rangeEnd`를 기준으로 하고, 클라이언트 계산은 기본 선택값으로만 쓴다. |
| 7 | 영향 라우트 MANAGER/MEMBER 메뉴·리다이렉트 브라우저 확인 | 서버 권한은 §4대로. `COMMUTE_APPROVER`가 3번째 역할로 추가됨 | **Frontend 확인 필요**(브라우저). 확인 범위에 `COMMUTE_APPROVER` 로그인(관리자 메뉴 미노출, 직접 URL 접근 시 서버 403)을 추가한다. |

### 7개 항목 밖에서 Frontend 설계와 다른 점·빠진 항목

1. **기존 발송 월 마감**: `CloseMonthConfirmModal`은 확인만 받는다. 서버는 `legacyDispatch: true`인 월에 `legacyResolution`(NO_CORRECTION_NEEDED / CORRECTED)과 `note`를 필수로 요구한다(`LEGACY_RESOLUTION_REQUIRED`). 상태 조회의 `legacyDispatch`로 입력 칸을 분기해야 한다.
2. **수신 불명 확인 UI**: `DELIVERY_COMMITTED`는 배지 안내만 있다. 확인 API(`POST /api/overtime/report/dispatch/confirmation`, outcome + note)를 호출하는 화면이 필요하다.
3. **확정본 다운로드**: `GET /api/overtime/report/final-excel`(보관본)과 기존 참고용 다운로드를 구분해 제공해야 한다. 보관본이 있는지는 `finalFileAvailable`로 판단한다.
4. **잠금 표시**: `CommuteDetail.lockReason`이 있으면 정정 신청과 퇴근 버튼을 막고 사유(마감 / 수신 확인 대기)를 표시한다.
5. **일반 퇴근 24시간 초과**: 퇴근 버튼은 `COMMUTE_END_WINDOW_EXPIRED`(409)를 받을 수 있다. 정정 신청 진입으로 안내한다.
6. **정정 가능 대상**: 서버는 `IN_PROGRESS` 미퇴근 기록의 정정 신청도 허용한다(현재 시각 이하). 화면에서 `CORRECTION_REQUIRED`·`COMPLETED`로만 제한해도 계약 위반은 아니다.
7. **승인 담당자 미지정**: `/api/auth/me`의 `correctionApprover`가 없는 MANAGER·COMMUTE_APPROVER는 신청이 409가 된다. 신청 버튼에서 미리 안내할 수 있다.
