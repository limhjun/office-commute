# 통합: commute-correction

| 항목 | 값 |
| --- | --- |
| 작업 식별자 | commute-correction |
| 상태 | **INTEGRATED (브라우저 주요 흐름 검증)** — 병합·자동 검증·브라우저 주요 흐름 완료. 일부 데이터 의존 화면이 남음 |
| 통합 브랜치 | `worktree-integrate` (기준 `fac7dc9`) |
| Backend 대상 커밋 | `worktree-backend` @ `4ae4fd2` (계약 `fd51868`·`346e55a`, 구현 `e1aef28`) |
| Frontend 대상 커밋 | `worktree-frontend` @ `ef0c0e1` (기준 `08a8353`, `2052d5b`·`08a8353` 은 계약 cherry-pick) |
| 시작 시각 | 2026-10-10 |

## 시작 전 대조 결과

- 두 브랜치 HEAD 가 인계 커밋과 일치한다.
- `git cherry`: Frontend 의 `2052d5b`·`08a8353` 은 Backend `fd51868`·`346e55a` 와 패치 동일.
- Backend `e1aef28..4ae4fd2` 는 `docs/handoff/` 문서만 변경.
- 작업자 worktree 파일을 대상 커밋 트리와 비교: 구현 변경 없음. 추가 파일은 Backend `spy.log`(실행 로그), Frontend `.handoff/`(미커밋 인계 메모)뿐.
- 인계 상태: Backend READY, Frontend READY(브라우저 미검증). READY 는 통합 준비 완료이며 전체 검증 완료가 아니다.

## 통합 기록

- `90b39e1`: Backend `4ae4fd2` 병합(충돌 없음).
- `e031fdc`: Frontend `ef0c0e1` 병합. `docs/handoff/commute-correction-backend.md` add/add 충돌 → Backend `4ae4fd2` 판을 채택(Frontend 쪽은 이전 판). `openapi.yml`·`schema.d.ts`·`docs/` 는 `4ae4fd2` 와 동일함을 확인.
- Frontend 미커밋 인계 메모를 `docs/handoff/commute-correction-frontend.md` 로 보존(원본 worktree 는 수정하지 않음).
- `docs/COMMUTE_CORRECTION_PLAN.md`(메인 체크아웃 미추적 파일)를 운영 문서 링크 대상이므로 함께 추가.

## 통합 중 수정

- 병합 충돌은 인계 문서 1건뿐이었고, 계약(`openapi.yml`)·생성 타입·프론트엔드 호출 사이 불일치는 build(tsc)와 `gen:api` drift 확인에서 발견되지 않았다.
- `0d13306`: 월 마감 확인 모달의 첫 안내 문장이 모달 밖으로 넘쳐 가로 스크롤이 생기던 문제 수정(Mantine `List.Item` nowrap + inline-flex). 브라우저에서 수정 전 재현, 수정 후 모든 항목이 모달 안쪽 16px 에 들어오고 가로 스크롤 요소 0개임을 확인.

## 통합 작업 목록

인계 문서의 건너뛴 검증·미검증 항목·알려진 문제를 모았다.

| # | 항목 | 출처 | 결과 |
| --- | --- | --- | --- |
| 1 | `./gradlew clean check --rerun-tasks` (JDK 21.0.12, 통합 결과) | 공통 | 통과. 358개, 실패 0, 건너뜀 0. #9 수정 후 `clean check` 재실행: 360개, 실패 0, 건너뜀 0 (Docker 실행 상태라 MySQL 6개 포함) |
| 2 | `pnpm --dir frontend lint` / `build` (Backend 인계의 `UNCLOSED` tsc 실패 해소 확인 포함) | Backend #3, 공통 | 통과. lint 0건, build 성공(vite 청크 크기 경고만) |
| 3 | `gen:api` drift 없음 | 공통 | 확인. 재생성 후 변경 없음 |
| 4 | MySQL/Flyway: `*MySqlIntegrationTest`(신규 `CommuteCorrectionMySqlIntegrationTest` 포함), V15·V16, `ddl-auto=validate`, UNIQUE 제약, `FOR UPDATE`, DATETIME(6) | Backend #1 | 통과. Docker 29.5.2, `mysql:8.4` Testcontainers. 6개 실행·통과(건너뜀 0) |
| 5 | MySQL InnoDB 잠금 동작(동시성 테스트는 H2 만 실행됨) | Backend #2 | 통과. `CommuteCorrectionConcurrentTest`(3)·`CommuteHistoryServiceConcurrentTest`(2)·`AnnualLeaveServiceConcurrentTest`(1)를 환경 변수로 `jdbc:tc:mysql:8.4` + Flyway + `validate` 로 바꿔 실행, 6개 통과. 결과 XML 에서 MySQL 접속·`for update` 실행 확인, H2 접속 없음. 코드 변경 없는 1회 수동 실행이며 CI 에는 포함되지 않음 |
| 6 | 브라우저: 화면 렌더링, 로딩·빈·오류 상태, 모달(DST overlap 포함), 역할별 메뉴·직접 URL 리다이렉트(MEMBER·MANAGER·COMMUTE_APPROVER), 409 후 재조회, 다운로드 파일명 | Frontend, Backend #4 | **대부분 통과** (아래 "브라우저 검증 상세"). 미검증: DST overlap 선택(Asia/Seoul 기록만 있음), 로딩 상태(로컬이라 순간적) |
| 7 | 월 마감 성공·마감 직후 발송·수신 확인·확정본 다운로드·기존 발송 월(`legacyDispatch`) 화면 | Frontend | 마감 성공·발송 FAILED 표시·확정본 다운로드 통과. **미검증**: 발송 재시도 클릭, 수신 확인(DELIVERY_COMMITTED 필요), `legacyDispatch` 화면(기존 SENT 월 데이터 없음). 실제 메일은 나가지 않음(수신자 미설정·닫힌 포트) |
| 8 | 담당자 미지정 요청 신청·처리 409, `PENDING_CORRECTION_EXISTS`, `COMMUTE_END_WINDOW_EXPIRED`, `CORRECTION_REQUIRED` 행, 월 경계 야간근무 `regularEndTarget` | Frontend | 서버 동작은 통합·컨트롤러 테스트로 통과. 일반 퇴근 대상·기한 표시는 브라우저 통과. **나머지 화면 표시 미검증**(dev 는 실제 Clock 이라 24시간 초과·과거 근무 데이터 준비 불가) |
| 9 | **알려진 문제**: 출근과 같은 시각 정정이 소수 초 때문에 `CORRECTION_END_BEFORE_START` 로 거부됨. `CommuteCorrectionService` 는 신청 시각을 초 단위로 자르고, `CommuteHistory.calculateCorrectedWorkingMinutes` 는 마이크로초를 가진 `workStartTime` 과 그대로 비교한다 | Frontend → Backend | **수정**: 신청 시각을 초 대신 마이크로초(DATETIME(6) 저장 정밀도)로 자른다. 계약의 "출근 시각 이상(동일 시각 허용 → 0분)" 을 지키고, 종료 ≥ 출근 불변식도 유지된다. `openapi.yml` 설명 문구 갱신(`schema.d.ts` 주석 1줄 재생성). 회귀 테스트 2개 추가(수정 전 코드에서 둘 다 실패 확인). 남는 화면 동작: 입력이 분 단위라 출근한 분 안의 시각(예: 09:00:20 출근 → 09:00)은 고를 수 없고 "출근 시각 이후여야 합니다" 로 안내된다 — 출근과 같은 분에 퇴근한 기록의 기본값도 이 경우다 |
| 10 | CSRF 운영 확인(Nginx `Host` 전달, 스테이징 403 여부) | Backend #5 | 배포 범위 — 미실행 |
| 11 | CI 도구 버전(Node 22 / pnpm 10) | Backend #6, Frontend | **통과(CI)**. PR #53 CI 에서 Node v22.23.3 / pnpm 10.34.6 으로 install `--frozen-lockfile`·lint·build 통과, backend `./gradlew check` 통과(JDK 21). 로컬은 Node 24.16 / pnpm 11.5.1, 셸 기본 JDK 25 라 `JAVA_HOME` 을 temurin 21 로 지정해 실행. CI 로그에 테스트 수가 없어 MySQL 테스트 실행 여부는 CI 에서 확인하지 못함 |
| 12 | 프론트엔드 단위 테스트 러너 없음 | Frontend | 범위 밖 |
| 13 | **발견(기존 버그)**: 로그아웃 후 화면이 이전 사용자로 남는다. `/login` 대신 역할 기본 화면에 머물고 헤더·메뉴가 이전 사용자 그대로이며, 이후 API 는 401. 원인: `AuthContext.logout` 의 `qc.clear()` 가 `['auth','me']` 쿼리를 제거해 `AuthProvider` 의 `useQuery` 관찰자가 끊기고, 뒤이은 `setQueryData` 는 새 쿼리에 써서 `user` 가 갱신되지 않음 → `LoginPage` 가 남은 `user` 로 홈에 되돌림 | 통합 브라우저 검증 | **수정**: `['auth','me']` 는 유지한 채 `null` 로 비우고 나머지 쿼리·뮤테이션 캐시만 제거. lint·build 통과. 브라우저 재확인 통과: MANAGER 로 `/employees` 에서 로그아웃 → `/login`, 헤더·메뉴 사라짐, `/api/auth/me` 401, 보호 URL 직접 접근 → `/login`, 콘솔 오류 없음 |

## 브라우저 검증 상세

환경: 통합 dev 서버(백엔드 8081, Vite 5174, H2), Claude in Chrome. dev 데이터만 변경했다(재기동 시 초기화).

| 영역 | 확인 내용 | 결과 |
| --- | --- | --- |
| MANAGER 메뉴·역할 | 8개 메뉴, "상위 승인자" 배지 | 통과 |
| 직원 | 정정 승인 담당자 열, 선택지(MANAGER→상위 승인자만, 상위 승인자→관리자만, 본인 제외), MEMBER 는 "전체 매니저" | 통과 |
| 월 마감 전 | 2026-09 "마감 가능", 집계 기간 2026-08-31~09-30, 빈 상태 문구, 참고용 Excel 만 노출 | 통과 |
| 마감 확인 모달 | 기존 발송 아님 → legacy 입력 없음, 메모 선택, 확인 체크 전 마감 버튼 비활성 | 통과 (넘침은 `0d13306` 로 수정) |
| 월 마감 실행 | 사용자 확인 후 2026-09 마감. 상태 "마감됨"·수동 마감·담당자·시각, 마감된 월 목록 | 통과 |
| 마감 직후 발송 | 원본 1회 시도 FAILED `MAIL_SEND_FAILED: report.mail.ceo 가 설정되지 않았습니다.`, `report_file` 보관 후 시도, 실패 알림도 수신자 없음으로 미발송(ERROR 로그) | 통과 |
| 확정본·참고용 다운로드 | final-excel 200, xlsx(PK), 파일명 `2026년9월_초과근무보고서.xlsx` / 참고용 `2026년9월_초과근무보고서_참고용.xlsx` | 통과 (fetch 로 확인, 파일 저장은 하지 않음) |
| 마감 순서 | 2026-09 마감 후 2026-08 도 "마감 가능"(계획 153행: 이전 월 마감을 선행 조건으로 요구하지 않음) | 계획과 일치 |
| 내 근태 | 출근 → 퇴근 대상·일반 퇴근 기한(Asia/Seoul) 표시, 같은 날 재출근 "오늘은 이미 출근을 기록했습니다.", 퇴근 → 정정 신청 버튼 | 통과 |
| 정정 신청 모달 | 사유 필수, 출근 이전 시각·미래 시각 필드 오류(요청 미발송), 예상 근무 시간, 신청 후 "정정 대기" 배지·버튼 비활성·원본 불변 | 통과. 단 출근과 같은 분에 퇴근한 기록은 기본값이 출근 이전으로 표시됨(#9 와 같은 원인) |
| 내 정정 요청 | 대기 요청·담당자·취소 버튼, MEMBER 빈 상태 | 통과 |
| 정정 승인(COMMUTE_APPROVER) | 4개 메뉴, `/closing`·`/employees` 직접 URL → `/me/commute`, API 403. 반려 사유 없이 → 필드 오류·요청 미발송, 서버도 400 `reason`. 의견 없이 승인 → 반영 토스트, 승인 탭에 처리자·시각 | 통과 |
| MEMBER | 3개 메뉴, `/approvals`·`/closing` 직접 URL → `/me/commute`, review API 403 | 통과 |
| 409 후 재조회 | 다른 경로로 취소된 요청을 화면에서 취소 → "이미 처리된 요청입니다." 후 목록이 취소 상태로 갱신 | 통과 |
| 콘솔 | 오류·예외 없음 | 통과 |

참고: 확장 도구의 네트워크 기록에 UI 로그아웃 POST 가 503 으로 남았으나, 같은 요청을 페이지 안에서 앱 클라이언트·fetch 로 보내면 200 이고 백엔드·Vite 로그에도 503 이 없다. 도구 기록 문제로 보고 확정하지 않았다.

## 하지 않은 일

- main 병합, 배포, 운영 데이터 정정(9월 정정 대상 특정·마감·정정본 발송), 실제 메일 발송.
- 작업자 worktree(`backend`, `frontend`) 수정.
