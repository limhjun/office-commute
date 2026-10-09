# 통합: commute-correction

| 항목 | 값 |
| --- | --- |
| 작업 식별자 | commute-correction |
| 상태 | **INTEGRATING** — 통합 시작, 검증 진행 중 |
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

## 통합 작업 목록

인계 문서의 건너뛴 검증·미검증 항목·알려진 문제를 모았다.

| # | 항목 | 출처 | 결과 |
| --- | --- | --- | --- |
| 1 | `./gradlew check` (통합 결과) | 공통 | 미실행 |
| 2 | `pnpm --dir frontend lint` / `build` (통합 결과, Backend 인계의 `UNCLOSED` tsc 실패 해소 확인 포함) | Backend #3, 공통 | 미실행 |
| 3 | `gen:api` drift 없음 | 공통 | 미실행 |
| 4 | MySQL/Flyway: `*MySqlIntegrationTest`(신규 `CommuteCorrectionMySqlIntegrationTest` 포함), V15·V16, `ddl-auto=validate`, UNIQUE 제약, `FOR UPDATE`, DATETIME(6) | Backend #1 | 미실행 |
| 5 | MySQL InnoDB 잠금 동작(동시성 테스트는 H2 만 실행됨) | Backend #2 | 미실행 |
| 6 | 브라우저: 화면 렌더링, 로딩·빈·오류 상태, 모달(DST overlap 포함), 역할별 메뉴·직접 URL 리다이렉트(MEMBER·MANAGER·COMMUTE_APPROVER), 409 후 재조회, 다운로드 파일명 | Frontend, Backend #4 | 미실행 |
| 7 | 월 마감 성공·마감 직후 발송·수신 확인·확정본 다운로드·기존 발송 월(`legacyDispatch`) 화면 | Frontend | 미실행 (실제 메일 발송 금지) |
| 8 | 담당자 미지정 요청 신청·처리 409, `PENDING_CORRECTION_EXISTS`, `COMMUTE_END_WINDOW_EXPIRED`, `CORRECTION_REQUIRED` 행, 월 경계 야간근무 `regularEndTarget` | Frontend | 미실행 |
| 9 | **알려진 문제**: 출근과 같은 시각 정정이 소수 초 때문에 `CORRECTION_END_BEFORE_START` 로 거부됨(계약 문구와 구현 불일치) | Frontend → Backend | 미결정 |
| 10 | CSRF 운영 확인(Nginx `Host` 전달, 스테이징 403 여부) | Backend #5 | 배포 범위 — 통합에서 미실행 |
| 11 | CI 도구 버전(Node 22 / pnpm 10) | Backend #6, Frontend | 미실행 |
| 12 | 프론트엔드 단위 테스트 러너 없음 | Frontend | 범위 밖 |
