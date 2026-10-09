# 인계: commute-correction — Backend

- 작업 식별자: `commute-correction`
- 담당 영역: Backend(서버·DB·테스트), API 계약(`openapi.yml`), 생성 타입(`frontend/src/api/schema.d.ts`)
- 상태: **READY**
  - Backend 범위는 통합할 수 있는 상태다(후속 보완 `346e55a`·`e1aef28` 반영).
  - MySQL/Flyway 검증은 Docker가 없어 실행하지 못했다. 통합 단계 필수 확인 항목이다(아래 "건너뛴 검증").
- 작업 브랜치: `worktree-backend`
- 계약 커밋: `fd51868`(최초), `346e55a`(후속 변경: 일반 퇴근 대상 추가, 담당자 미지정 신청 허용) — `openapi.yml`, 생성 타입, `docs/COMMUTE_CORRECTION_API.md`
- 최종 구현 커밋: `e1aef28c851016a9214949854ac7c8ed1cc5804e` (`e1aef28`). 이전 구현 커밋 `d2c146c`에 후속 수정을 더한 것이며, 공유 커밋은 재작성하지 않았다.
- 이 인계 파일은 구현 커밋 뒤의 별도 문서 커밋이다. 코드 변경은 없다.
- Frontend 연결 인계(실행 방법·테스트 계정·Frontend 7개 확인 항목 대조): [commute-correction-frontend-integration.md](commute-correction-frontend-integration.md)

## 구현한 기능

| 영역 | 내용 |
| --- | --- |
| 출퇴근 | 과거 미퇴근이 있어도 새 근무일 출근을 허용한다(`PREVIOUS_COMMUTE_NOT_ENDED` 삭제). 일반 퇴근은 최신 실제 근무만 대상이며 출근부터 정확히 24시간까지 허용한다(`COMMUTE_END_WINDOW_EXPIRED`). 과거 미퇴근을 대신 닫지 않는다. |
| 근태 상태 | `UNCLOSED` → `CORRECTION_REQUIRED`(24시간 초과 또는 후속 실제 근무, 조회 월 밖 포함). 월 조회에 `commuteHistoryId`, `version`, `workZone`, `pendingCorrectionRequestId`, `lockReason`을 추가했다. |
| 원본 버전 | `commute_history.version`. 일반 퇴근·정정 승인의 조건부 UPDATE가 직접 +1 한다. |
| 정정 요청 | 신청·승인·반려·취소. 요청 테이블이 이력을 겸한다. `uk_correction_request_pending`으로 기록당 대기 1건을 보장한다. 승인 시 권한·상태·버전·마감·후속 근무를 재검사하고, 원본 갱신과 요청 전이를 한 트랜잭션으로 묶는다. |
| 권한 | `COMMUTE_APPROVER` 역할을 추가했다. 담당자 지정은 `PUT /api/employee/{id}/correction-approver`. 자기 승인을 금지한다. 대기 요청이 있으면 담당자 변경과 퇴사일 지정을 차단한다. 역할은 매 요청 DB에서 다시 읽는다. |
| 월 마감 | `GET/POST /api/monthly-closings`, `GET /api/monthly-closings/{yearMonth}`. Asia/Seoul 기준 끝난 월만 마감한다. 입력 기간(월 1일이 속한 주의 월요일~월말)에 미퇴근·대기 요청이 있으면 거부한다. 마감 후 그 기간의 모든 근태 쓰기를 차단한다. |
| 초기 전환 | 기존 SENT 월은 `legacyResolution`(NO_CORRECTION_NEEDED / CORRECTED)이 필수다. 겹치는 이전 기존 발송 월이 남았으면 다음 월 마감을 차단한다. DELIVERY_COMMITTED 월은 정정·마감을 임시 차단하고 운영자 확인 API를 둔다. |
| 보고서 | 모든 발송 진입점이 마감을 확인한다(`MONTH_NOT_CLOSED`). 최종 Excel을 `report_file`에 보관한 뒤 발송하고, 재시도는 보관본을 쓴다. 정정본은 `kind = CORRECTION`으로 원본과 분리한다. `final-excel` 다운로드를 추가했다. 참고용 Excel은 파일명과 시트 문구로 구분한다. |
| 동시성 | 근태 쓰기는 소유 직원 행을, 마감·퇴사·담당자 지정은 전체 직원 행을 `SELECT … FOR UPDATE`로 잠근다(ID 오름차순). |
| CSRF | 상태 변경 요청은 Origin/Referer가 같은 출처여야 한다(`CSRF_ORIGIN_REJECTED`). dev·mysql 프로파일은 `http://localhost:5173`을 허용한다. |

## 변경 파일(주요)

- DB: `src/main/resources/db/migration/V15__commute_correction.sql`, `V16__monthly_closing_and_report_file.sql`, `data.sql`(dev 상위 승인자 계정 `approver@company.com`, 관리자와 상호 지정 — 비밀번호는 `data.sql` 주석 참조)
- 도메인: `domain/commute/CommuteHistory`, `domain/employee/{Employee,Role}`, `domain/correction/*`, `domain/closing/*`, `domain/report/{ReportDispatch,ReportFile,ReportKind,ReportFinality,DispatchFailureReason}`
- 서비스: `service/commute/{CommuteHistoryService,CommuteWriteLock}`, `service/correction/CommuteCorrectionService`, `service/closing/{MonthlyClosingService,CommutePeriodGuard,ProtectedPeriods}`, `service/report/OverTimeReportDispatchService`, `service/employee/EmployeeService`, `service/overtime/*`
- 웹: `controller/correction`, `controller/closing`, `controller/{employee,overtime}`, `auth/{AuthInterceptor,OriginCheckInterceptor}`, `config/WebConfig`, `global/exception/GlobalExceptionHandler`
- 문서: `docs/COMMUTE_CORRECTION_API.md`(계약 인계), `docs/COMMUTE_CORRECTION_OPERATIONS.md`(보관·백업·전환 절차·잠금 원칙)

## 실행한 검증

| 검증 | 결과 |
| --- | --- |
| `./gradlew openApiValidate` | Spec is valid |
| 가까운 테스트(도메인·서비스 단위 → 통합 → 동시성 → 컨트롤러) | 통과 |
| `./gradlew clean check --rerun-tasks` (Java 21, 커밋 `e1aef28`) | BUILD SUCCESSFUL. 358개 실행, 실패 0, 건너뜀 6 |
| `pnpm --dir frontend gen:api` | `schema.d.ts` 재생성(계약 커밋에 포함) |

추가한 테스트:
- `CommuteCorrectionIntegrationTest`(21개): 승인·반려·취소, 반복 정정, 26시간 정정, 권한·자기 승인, 버전 충돌, 다른 기록 퇴근 무관, 후속 출근 재검사, 승인 롤백, 월 마감·전월 말 보호, 월 경계, 기존 발송 월 전환, 수신 불명 차단
- `CommuteCorrectionConcurrentTest`(3개): 동시 신청, 동시 승인·반려·취소, 마감과 신청 경쟁
- `CommuteCorrectionControllerTest`, `MonthlyClosingControllerTest`: 검증 오류, 오류 코드·상태, CSRF, 이전 세션 역할 불신, COMMUTE_APPROVER의 관리자 기능 차단
- 일반 퇴근 24시간 경계와 근무 분 절삭(0초·59초·60초)은 도메인 테스트, 정정 시각 검증은 서비스 통합 테스트로 확인
- 디스패치 서비스: 마감 전 보류, 보관 후 발송, 재시도 시 보관본 사용, 보관 실패 시 미발송, 정정본 분리, 수신 확인 전이

## 건너뛴 검증 — 통합 단계에서 확인할 항목

1. **MySQL/Flyway(미검증, Docker 없음)**: `*MySqlIntegrationTest` 6개가 건너뛰어졌다. 통과로 보지 않는다. 신규 `CommuteCorrectionMySqlIntegrationTest` 4개가 포함된다. Docker 환경에서 `./gradlew test --tests '*MySqlIntegrationTest'`를 실행해 다음을 확인한다.
   - V15·V16 적용과 `ddl-auto=validate` 통과. 특히 `report_file.content LONGBLOB` / `sha256 CHAR(64)` 검증, `DROP INDEX uk_report_dispatch_year_month`
   - `uk_correction_request_pending`, `uk_report_dispatch_year_month_kind`
   - `SELECT … FOR UPDATE` 잠금 쿼리, DATETIME(6) 정밀도
2. **MySQL 잠금 동작**: 동시성 테스트는 H2에서만 실행했다. InnoDB REPEATABLE READ에서 "잠금을 트랜잭션 첫 문장으로" 원칙이 기대대로 동작하는지 MySQL로 재확인이 필요하다.
3. **Frontend 빌드**: 계약 커밋 이후 기존 `MyCommutePage.tsx`가 `UNCLOSED`를 참조해 `tsc`가 실패한다. Frontend 세션 담당이며 Backend에서는 수정하지 않았다. `pnpm --dir frontend lint/build`는 통합 시 실행한다.
4. **브라우저 확인**: 역할별 신청·승인·마감·발송 화면은 실행하지 않았다(Frontend 범위).
5. **CSRF 운영 확인**: 운영 Nginx는 `Host`를 그대로 전달하므로 같은 출처 판정이 통과해야 한다. 배포 전 스테이징에서 로그인·쓰기 요청이 403이 아닌지 확인한다.
6. **도구 버전**: 이 환경은 Node 24 / pnpm 11.5이다(CI는 Node 22 / pnpm 10). `pnpm install --frozen-lockfile`과 `gen:api`는 잠금 파일 변경 없이 성공했다.

## 미완료·제약 사항

- 역할 변경 API는 원래 없어서 추가하지 않았다. 계정 비활성화는 퇴사일 지정으로 간주해 대기 요청이 있으면 차단했다. 역할 변경 API를 추가할 때는 같은 검사(`hasPendingCorrectionInvolving`)와 전체 잠금을 적용해야 한다.
- 기존 발송 월의 원본 파일 업로드 기능은 없다. 확보 여부는 마감 `note`에 기록한다(정책 3.3: 재생성 파일을 원본으로 표시하지 않음).
- 실제 운영 데이터 전환(9월 정정 대상 특정, 마감, 정정본 발송)은 수행하지 않았다. 배포, 운영 데이터 변경, 메일 발송도 하지 않았다.
- `docs/COMMUTE_CORRECTION_PLAN.md`는 메인 체크아웃의 미추적 파일이며 이 브랜치에 포함되지 않았다. 운영 문서의 링크가 이 파일을 가리키므로 통합 시 함께 커밋해야 한다.
- 공유 이후 API 계약 변경: **있음 — `346e55a`**. 상세와 Frontend 영향은 [Frontend 연결 인계 §1-1](commute-correction-frontend-integration.md)에 있다.

## 후속 보완 (`346e55a` 계약, `e1aef28` 구현)

1. **일반 퇴근 대상 식별**
   - 기존 월별 응답에는 `PUT /api/commute`가 종료할 기록이 없었다. 화면이 `details`의 `IN_PROGRESS`로 추측해야 했고, 9/30 22:00 시작 야간근무를 10월로 조회하면 대상이 목록에 없었다.
   - `regularEndTarget`을 추가했다. 대상은 최신 실제 근무, 미퇴근, 24시간 이내이며 조회 월과 무관하다.
   - 판정은 `CommuteHistory.isRegularEndableAt`으로, 일반 퇴근(`calculateRegularEndMinutes`)과 같은 경계를 쓴다.
2. **담당자 미지정 처리**
   - 계획 1.3은 "담당자가 없으면 관리자·상위 승인자의 요청은 **승인할 수 없다**", "담당자 변경 = 대기 요청 취소 → 변경 → 재신청"이다.
   - 기존 구현은 신청 자체를 409로 거부해 합의보다 범위가 넓었다.
   - 이제 신청은 받고, 승인·반려만 `CORRECTION_APPROVER_NOT_ASSIGNED`로 막는다. 대기 중 미지정 → 지정도 "변경"으로 보고 막는다(취소 → 지정 → 재신청).
   - `CorrectionReviewPolicy`는 신청 당시 지정 승인자 스냅샷과 현재 지정 관계가 같은지도 검사한다.
   - 결과: 담당자 미지정 대기 요청도 월 마감을 막는다. 처리할 수 없는 요청이 마감을 막지 않게 하려면 신청자가 취소해야 한다.
