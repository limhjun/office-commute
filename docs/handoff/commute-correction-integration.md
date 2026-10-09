# 통합: commute-correction

| 항목 | 값 |
| --- | --- |
| 작업 식별자 | commute-correction |
| 상태 | **INTEGRATED (브라우저 미검증)** — 병합·자동 검증 완료. 브라우저 확인과 #9 결정이 남음 |
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

- 코드 수정 없음. 병합 충돌은 인계 문서 1건뿐이었고, 계약(`openapi.yml`)·생성 타입·프론트엔드 호출 사이 불일치는 build(tsc)와 `gen:api` drift 확인에서 발견되지 않았다.

## 통합 작업 목록

인계 문서의 건너뛴 검증·미검증 항목·알려진 문제를 모았다.

| # | 항목 | 출처 | 결과 |
| --- | --- | --- | --- |
| 1 | `./gradlew clean check --rerun-tasks` (JDK 21.0.12, 통합 결과) | 공통 | 통과. 358개, 실패 0, 건너뜀 0 (Docker 실행 상태라 MySQL 6개 포함) |
| 2 | `pnpm --dir frontend lint` / `build` (Backend 인계의 `UNCLOSED` tsc 실패 해소 확인 포함) | Backend #3, 공통 | 통과. lint 0건, build 성공(vite 청크 크기 경고만) |
| 3 | `gen:api` drift 없음 | 공통 | 확인. 재생성 후 변경 없음 |
| 4 | MySQL/Flyway: `*MySqlIntegrationTest`(신규 `CommuteCorrectionMySqlIntegrationTest` 포함), V15·V16, `ddl-auto=validate`, UNIQUE 제약, `FOR UPDATE`, DATETIME(6) | Backend #1 | 통과. Docker 29.5.2, `mysql:8.4` Testcontainers. 6개 실행·통과(건너뜀 0) |
| 5 | MySQL InnoDB 잠금 동작(동시성 테스트는 H2 만 실행됨) | Backend #2 | 통과. `CommuteCorrectionConcurrentTest`(3)·`CommuteHistoryServiceConcurrentTest`(2)·`AnnualLeaveServiceConcurrentTest`(1)를 환경 변수로 `jdbc:tc:mysql:8.4` + Flyway + `validate` 로 바꿔 실행, 6개 통과. 결과 XML 에서 MySQL 접속·`for update` 실행 확인, H2 접속 없음. 코드 변경 없는 1회 수동 실행이며 CI 에는 포함되지 않음 |
| 6 | 브라우저: 화면 렌더링, 로딩·빈·오류 상태, 모달(DST overlap 포함), 역할별 메뉴·직접 URL 리다이렉트(MEMBER·MANAGER·COMMUTE_APPROVER), 409 후 재조회, 다운로드 파일명 | Frontend, Backend #4 | **미검증**. 통합 세션에서 Claude in Chrome 연결 실패. 통합 dev 서버(백엔드 8081, Vite 5174) 기동·프록시 401 응답까지만 확인 |
| 7 | 월 마감 성공·마감 직후 발송·수신 확인·확정본 다운로드·기존 발송 월(`legacyDispatch`) 화면 | Frontend | **화면 미검증**. 서비스 경로는 자동 테스트로 확인. 실제 메일 발송 금지 |
| 8 | 담당자 미지정 요청 신청·처리 409, `PENDING_CORRECTION_EXISTS`, `COMMUTE_END_WINDOW_EXPIRED`, `CORRECTION_REQUIRED` 행, 월 경계 야간근무 `regularEndTarget` | Frontend | 서버 동작은 통합·컨트롤러 테스트로 통과. **화면 표시 미검증**(dev 는 실제 Clock 이라 24시간 초과·과거 근무 데이터 준비 불가) |
| 9 | **알려진 문제**: 출근과 같은 시각 정정이 소수 초 때문에 `CORRECTION_END_BEFORE_START` 로 거부됨. `CommuteCorrectionService` 는 신청 시각을 초 단위로 자르고, `CommuteHistory.calculateCorrectedWorkingMinutes` 는 마이크로초를 가진 `workStartTime` 과 그대로 비교한다 | Frontend → Backend | **미결정 — 수정하지 않음**. 비교 정밀도·저장값을 정하는 도메인 규칙 변경이라 계약 담당 결정 필요. 화면은 분 단위만 보내므로 사용자 영향은 출근한 분 안의 0분 정정 불가 정도 |
| 10 | CSRF 운영 확인(Nginx `Host` 전달, 스테이징 403 여부) | Backend #5 | 배포 범위 — 미실행 |
| 11 | CI 도구 버전(Node 22 / pnpm 10) | Backend #6, Frontend | **미실행**. 로컬은 Node 24.16 / pnpm 11.5.1, Node 22 미설치. 셸 기본 JDK 25 라 `JAVA_HOME` 을 temurin 21 로 지정해 실행 |
| 12 | 프론트엔드 단위 테스트 러너 없음 | Frontend | 범위 밖 |

## 하지 않은 일

- main 병합, 배포, 운영 데이터 정정(9월 정정 대상 특정·마감·정정본 발송), 실제 메일 발송.
- 작업자 worktree(`backend`, `frontend`) 수정.
