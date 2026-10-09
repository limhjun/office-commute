# 인계: commute-correction — frontend

| 항목 | 값 |
| --- | --- |
| 작업 식별자 | commute-correction |
| 담당 영역 | frontend (`frontend/src/**`, `frontend/AGENTS.md`) |
| 상태 | **READY (브라우저 미검증)** — 화면·hooks 연결 완료, API 스모크 통과. 실제 브라우저 확인은 미실행 |
| 작업 브랜치 | `worktree-frontend` |
| 기준 커밋 | `08a8353` (`0beea38` 공통 컴포넌트 → `2052d5b` = cherry-pick `fd51868` 계약 → `08a8353` = cherry-pick `346e55a` 계약 보완) |
| 최종 구현 커밋 | `ef0c0e1` (아래 파일 목록) |
| 작성 시각 | 2026-10-10 |

> 이 파일은 커밋하지 않은 인계 메모다. 구현 커밋 SHA 를 바꾸지 않고 main 에 섞이지 않도록 worktree 에만 둔다.

## 계약 반영

- `fd51868`(계약)·`346e55a`(일반 퇴근 대상·담당자 미지정 신청 허용 보완)를 각각 한 번 cherry-pick 했다. 중복 없음(`git cherry` 확인).
- `pnpm --dir frontend gen:api` 재생성 결과가 커밋된 `schema.d.ts` 와 동일(diff 없음). `openapi.yml`·`schema.d.ts`·서버·DB 는 직접 수정하지 않았다.
- `346e55a` 반영 시점의 Backend 인계 상태는 WORKING 이었다. 사용자 확인 후 반영했다. Backend 가 이후 계약을 더 바꾸면 다시 대조해야 한다.

## 구현한 기능 (`ef0c0e1`)

| 영역 | 파일 | 내용 |
| --- | --- | --- |
| 오류 문구·필드 매핑 | `lib/errorMessages.ts`, `lib/notify.ts` | 계약 §4 코드별 문구. 시각 오류 4종(`CORRECTION_END_BEFORE_START`/`END_IN_FUTURE`/`NO_CHANGE`/`OVERLAPS_NEXT_WORK`)과 `INVALID_JSON` → 시각 입력 필드, `requestedWorkEndTime`/`reason` fieldErrors → 각 필드. `notifyWarning` 추가 |
| 역할 | `lib/roles.ts`, `App.tsx`, `AppLayout.tsx`, `main.tsx` | `COMMUTE_APPROVER`(상위 승인자) 라벨, `/approvals` 는 MANAGER·COMMUTE_APPROVER, `/closing` 은 MANAGER, `/me/corrections` 는 전 역할. 403 `FORBIDDEN` 시 `/api/auth/me` 재조회 |
| hooks | `hooks/useCommuteCorrections.ts`, `hooks/useMonthlyClosings.ts`, `hooks/useEmployees.ts`, `hooks/useCommute.ts` | 신청·내 이력·처리 목록·취소·승인·반려 / 마감 목록·상태·마감·발송 재시도·수신 확인 / 담당자 지정. 성공과 409 에서 근태(전 월)·요청·마감 상태 무효화. 출퇴근은 성공·실패 모두 근태 재조회 |
| 내 근태 | `pages/MyCommutePage.tsx` | `CORRECTION_REQUIRED`(정정 필요), 미퇴근 "미확정", 정정 대기·잠금 배지, 행별 정정 신청(완료·정정 필요 기록만; 잠금·대기·연차는 비활성). 퇴근 버튼은 `regularEndTarget` 이 있고 `lockReason` 이 없을 때만 활성, 대상 근무일·출근·일반 퇴근 기한 표시, 대상 행 표시. 담당자 미지정 MANAGER·COMMUTE_APPROVER 에게 신청 모달 안내(차단하지 않음) |
| 내 정정 요청 | `pages/MyCorrectionsPage.tsx` | 상태 필터, 전후 시각·근무 분, 사유, 처리자·의견, 취소(`actions.canCancel`), 담당자 미지정 대기 요청 안내 |
| 정정 승인 | `pages/ApprovalsPage.tsx` | 처리 대상 목록(기본 PENDING), 신청자·역할, 승인(의견 선택 `comment`)·반려(사유 필수 `reason`) |
| 직원 | `pages/EmployeesPage.tsx` | 역할에 상위 승인자 추가, 정정 승인 담당자 열(MANAGER→COMMUTE_APPROVER, COMMUTE_APPROVER→MANAGER, 본인 제외, MEMBER 는 "전체 매니저") |
| 월 마감 | `pages/MonthlyClosingPage.tsx`, `components/closing/*` | 상태·보호 기간·차단 사유·미해결 목록, 마감 확인(기존 발송 월은 `legacyResolution`+`note` 필수), 발송 이력(원본/정정본, 마감 전 보류 vs 실패 구분), 발송 재시도, 수신 확인, 확정본 다운로드, 마감 목록 |
| 다운로드 구분 | `pages/OvertimePage.tsx`, `pages/MonthlyClosingPage.tsx` | 기존 Excel 은 "참고용"으로 표시, 확정본은 `final-excel`(`finalFileAvailable` 일 때) |
| 공통 | `lib/zonedTime.ts`(`parseIsoMs` 마이크로초 대응), `lib/dispatch.ts`, `lib/month.ts`(`formatInstant` UTC 처리 시각), `components/correction/*` | |
| 문서 | `frontend/AGENTS.md` | 가드·hooks·lib 구조 갱신 |

## 실행한 검증

| 검증 | 결과 |
| --- | --- |
| `pnpm --dir frontend lint` | 통과 (오류·경고 0) |
| `pnpm --dir frontend build` | 통과. vite 청크 크기 경고만 (기존과 같은 성격) |
| `gen:api` 재생성 drift | 없음 |
| `zonedTime` 변환 (계약 전) | Node 로 Seoul·New York DST gap/overlap·Kolkata +05:30·KST 연말 경계 확인 |
| API 스모크 (계약 후) | Vite 프록시(5173) 경유, SPA 와 같은 경로·본문·Origin. 39/39 통과. 실행한 Backend 는 사용자가 띄운 backend worktree 의 dev 서버(응답에 `regularEndTarget` 이 있어 `346e55a` 이후 구현으로 판단) |

스모크 항목: 역할별 로그인·`/me`(`correctionApprover`), 상위 승인자의 관리자 API 403, MEMBER `/review` 403, 출근 후 `commuteHistoryId`·`version`·`workZone`·`regularEndTarget`, 시각 오류 코드(출근 이전·미래·오프셋 누락 `INVALID_JSON`·공백 사유 fieldError·버전 충돌), 신청·중복 대기 409·`pendingCorrectionRequestId`·취소·재취소 409, 반려 사유 필수·외부 Origin 403 `CSRF_ORIGIN_REJECTED`·반려, 관리자 `/review`·승인 후 COMPLETED·version+1·퇴근 대상 사라짐·`COMMUTE_ALREADY_ENDED`, 내 이력 3건, 마감 상태(전월 closable, 이번 달 `MONTH_NOT_ENDED`)·현재 월 마감 400·마감 목록·마감 전 발송 `MONTH_NOT_CLOSED` 보류·확정본 404·자기 지정 400.

스모크 부수 효과: dev H2 에 테스트 MEMBER·정정 요청 이력·2026-09 ORIGINAL 발송 FAILED(`MONTH_NOT_CLOSED`) 행이 생겼다. 재기동하면 초기화된다. 월 마감 POST 는 실행하지 않았다.

## 미검증 항목

- **브라우저 확인 전부**: 이 세션에 Claude in Chrome·내장 브라우저 도구가 없어 실행하지 못했다. 화면 렌더링, 로딩·빈·오류 상태, 모달 동작(DST overlap 선택 포함), 역할별 메뉴·직접 URL 리다이렉트(MEMBER·MANAGER·COMMUTE_APPROVER), 409 후 재조회 UI, 다운로드 파일명은 API 수준으로만 확인했다.
- 월 마감 성공 경로·마감 직후 발송·수신 확인·확정본 다운로드 성공·기존 발송 월(`legacyDispatch`) 화면: dev 데이터·메일 설정으로 재현하지 않았다.
- 승인자 미지정 요청의 신청 성공·처리 409, `PENDING_CORRECTION_EXISTS`(대기 중 담당자 변경), `COMMUTE_END_WINDOW_EXPIRED`(24시간 초과)·`CORRECTION_REQUIRED` 행·월 경계 야간근무의 `regularEndTarget` 표시: 데이터 준비가 필요해 미실행.
- CI 도구 버전: 로컬 Node 24.16 / pnpm 11.5.1. CI 기준(Node 22 / pnpm 10)으로는 실행하지 않았다.
- 프론트엔드 테스트 러너 없음(`zonedTime` 등 단위 테스트 미작성).

## Backend·계약에 전달할 사항

1. **출근과 같은 시각 정정이 소수 초 때문에 거부됨**: 계약은 "출근 시각 이상(동일 시각 허용 → 0분)"이지만, 서버는 신청 시각의 초 미만만 버리고 저장된 `workStartTime` 은 마이크로초를 유지한다(예: `…00:08:22.172696`). 신청 시각을 출근과 같게 보내면 `CORRECTION_END_BEFORE_START` 가 난다. 화면은 분 단위(`:00`)만 보내고 같은 기준으로 클라이언트 검증하므로 사용자에게는 일관되지만, 계약 문구와 구현이 다르다. 출근 시각도 초 단위로 비교할지, 문구를 고칠지 결정이 필요하다.
2. 그 밖의 계약 보완 요청은 없다.
