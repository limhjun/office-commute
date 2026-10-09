# 퇴근 시각 정정·월 마감 API 계약 (인계 문서)

- 작업 식별자: `commute-correction`
- 기준 정책: `docs/COMMUTE_CORRECTION_PLAN.md` (Issue #52, 운영 정책 확정본)
- 진실의 원천: `openapi.yml`. 이 문서는 프론트엔드 구현용 요약이며, 다르면 `openapi.yml`이 우선한다.
- 생성 타입: `frontend/src/api/schema.d.ts` (`pnpm --dir frontend gen:api`, 직접 편집 금지)

## 1. 역할별 권한

| 기능 | MEMBER | MANAGER | COMMUTE_APPROVER |
| --- | --- | --- | --- |
| 본인 출퇴근·근태 조회 (`/api/commute`) | O | O | O |
| 본인 연차 (`/api/annual-leave`) | O | O | O |
| 정정 신청·내 이력·취소 | O | O (지정 승인자 필요) | O (지정 승인자 필요) |
| 정정 처리 목록 (`/review`) | 403 | MEMBER 요청 전체 + 본인 지정 요청 | 본인 지정 요청만 |
| 승인·반려 | 403 | MEMBER 요청, 본인이 지정된 COMMUTE_APPROVER 요청 | 본인이 지정된 MANAGER 요청 |
| 직원·팀·초과근무·Excel·발송·월 마감 | 403 | O | 403 |
| 승인 담당자 지정 | 403 | O | 403 |

- 자기 승인은 항상 금지 (`403 CORRECTION_SELF_APPROVAL`).
- 역할은 **매 요청마다 DB에서 다시 읽는다**. 세션 생성 이후 바뀐 역할로 즉시 판정한다. `/api/auth/me` 응답의 `role`도 현재 DB 값이다.
- 라우팅 권장: COMMUTE_APPROVER는 본인 근태·연차와 정정 처리 화면만 연다. 기존 관리자 화면(직원·팀·초과근무)은 열지 않는다.

## 2. 변경된 기존 API

### `POST /api/commute` (출근)
- 과거 미퇴근이 있어도 새 근무일 출근 허용. **`PREVIOUS_COMMUTE_NOT_ENDED` 오류 코드는 삭제됐다.** 프론트엔드에서 이 코드의 처리도 제거한다.
- 409 `DUPLICATE_WORK`(같은 근무일), `CLOSING_PERIOD_LOCKED`, `REPORT_DELIVERY_UNCERTAIN`.

### `PUT /api/commute` (일반 퇴근)
- 대상: 가장 최근에 시작한 실제 근무(연차 제외). 출근부터 정확히 24시간까지 허용.
- 400 `COMMUTE_NOT_STARTED`. 409 `COMMUTE_ALREADY_ENDED`(최신 근무가 이미 종료됨, 과거 미퇴근을 대신 닫지 않음), `COMMUTE_END_WINDOW_EXPIRED`(24시간 초과, 정정 신청 안내), `CLOSING_PERIOD_LOCKED`, `REPORT_DELIVERY_UNCERTAIN`.

### `GET /api/commute?yearMonth=2026-09`

```json
{
  "details": [
    {
      "commuteHistoryId": 41,
      "version": 0,
      "date": "2026-09-02",
      "workZone": "Asia/Seoul",
      "workStartTime": "2026-09-02T09:01:00+09:00",
      "workingMinutes": 0,
      "usingDayOff": false,
      "status": "CORRECTION_REQUIRED",
      "pendingCorrectionRequestId": 7
    },
    {
      "commuteHistoryId": 42,
      "version": 1,
      "date": "2026-09-03",
      "workZone": "Asia/Seoul",
      "workStartTime": "2026-09-03T09:00:00+09:00",
      "workEndTime": "2026-09-03T18:30:00+09:00",
      "workingMinutes": 570,
      "usingDayOff": false,
      "status": "COMPLETED",
      "lockReason": "MONTH_CLOSED"
    }
  ],
  "sumWorkingMinutes": 570
}
```

- `status` enum: `COMPLETED | IN_PROGRESS | CORRECTION_REQUIRED | DAY_OFF`. **`UNCLOSED`는 삭제됐다** (`MyCommutePage.tsx`가 현재 이 값을 사용하므로 `tsc`가 실패한다).
  - `CORRECTION_REQUIRED` = 미퇴근이면서 출근 후 24시간이 지났거나 후속 실제 근무가 있음. 조회 월 밖의 후속 근무도 반영한다. 일반 퇴근이 불가하다는 뜻이며 신청 여부와는 무관하다.
  - 완료 기록에 대기 정정이 있어도 `COMPLETED`이다. 요청 상태는 `pendingCorrectionRequestId`로 함께 표시한다.
- `lockReason` (생략 가능): `MONTH_CLOSED`(마감 보호 기간), `DELIVERY_UNCERTAIN`(수신 미확인 보고서의 입력 기간). 값이 있으면 정정 신청 버튼을 비활성화한다.
- null 필드는 응답에서 **생략**된다 (`non_null` 직렬화).
- 미퇴근 기록의 `workingMinutes: 0`은 확정값이 아니다. 화면에서 "미확정"으로 표시하는 것을 권장한다.

### `GET /api/auth/me`, `GET /api/employee`
- `role` enum에 `COMMUTE_APPROVER`가 추가됐다.
- `correctionApprover: { employeeId, name, employeeCode }` (선택 필드)가 추가됐다.
- `POST /api/employee`에 `role: "COMMUTE_APPROVER"`를 넣으면 승인 전용 계정이 생성된다.

### `PUT /api/employee/{id}/retirement`
- 퇴사일 지정은 그 직원이 신청자이거나 지정 승인자인 PENDING 요청이 있으면 409 `PENDING_CORRECTION_EXISTS`로 거부된다. 퇴사 취소(null)는 막지 않는다.

### `POST /api/overtime/report/dispatch`
- 월 마감 전이면 발송하지 않는다: `status: FAILED`, `lastFailureReason: "MONTH_NOT_CLOSED: ..."`. 이는 장애가 아닌 보류 상태이므로 UI에서 실제 발송 장애와 구분해 표시한다.
- 응답에 `kind`, `lastAttemptedAt`, `finalFileAvailable`, `deliveryConfirmed*`가 추가됐다. `status`에 `DELIVERY_COMMITTED`가 추가됐다.

### `GET /api/overtime/report/excel`
- 항상 **참고용**이다. 파일명은 `YYYY년M월_초과근무보고서_참고용.xlsx`이다. 확정본은 `final-excel`을 사용한다.

## 3. 신규 API

### 정정 요청

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/commute-corrections` | 신청 (201) |
| GET | `/api/commute-corrections/mine?status=` | 내 요청 전체 |
| GET | `/api/commute-corrections/review?status=` | 처리 대상 목록 (MANAGER·COMMUTE_APPROVER) |
| GET | `/api/commute-corrections/{requestId}` | 단건 |
| POST | `/api/commute-corrections/{requestId}/cancel` | 신청자 취소 |
| POST | `/api/commute-corrections/{requestId}/approve` | 승인, 본문 `{ "comment"?: string }` 선택 |
| POST | `/api/commute-corrections/{requestId}/reject` | 반려, 본문 `{ "reason": string }` 필수 |

신청 요청:

```json
{
  "commuteHistoryId": 41,
  "commuteVersion": 0,
  "requestedWorkEndTime": "2026-09-02T19:30:00+09:00",
  "reason": "퇴근 버튼을 누르지 못함"
}
```

- `requestedWorkEndTime`은 **오프셋을 반드시 포함**해야 한다. 오프셋이 없으면 400 `INVALID_JSON`. 서버는 UTC Instant로 변환하고 초 미만을 버린다.
- 현지 시각 → 오프셋 변환은 기록의 `workZone` 기준이다(브라우저 시간대가 아니다). DST로 존재하지 않는 시각은 입력에서 막고, 중복되는 시각은 오프셋을 선택하게 한다.
- `reason`은 trim 후 1~500자다. 위반 시 `VALIDATION_ERROR`의 `fieldErrorResults[].field = "reason"`.

응답 (`CorrectionRequestResponse`):

```json
{
  "requestId": 7,
  "status": "PENDING",
  "commuteHistoryId": 41,
  "commuteVersion": 0,
  "requester": { "employeeId": 3, "name": "홍길동", "employeeCode": "EMP0003" },
  "requesterRole": "MEMBER",
  "workDate": "2026-09-02",
  "workZone": "Asia/Seoul",
  "workStartTime": "2026-09-02T09:01:00+09:00",
  "previousWorkingMinutes": 0,
  "requestedWorkEndTime": "2026-09-02T19:30:00+09:00",
  "requestedWorkingMinutes": 629,
  "reason": "퇴근 버튼을 누르지 못함",
  "requestedAt": "2026-10-09T01:00:00Z",
  "actions": { "canCancel": true, "canReview": false }
}
```

- 처리 후 `processedBy`, `processedAt`, `reviewComment`가 채워진다. 취소 시 `processedBy`는 신청자 본인이다.
- `assignedApprover`는 MANAGER·COMMUTE_APPROVER 요청에만 있다(신청 당시 지정 승인자).
- `previousWorkEndTime`은 완료 기록 정정일 때만 있다.
- `actions`는 표시용 값이다. 서버가 다시 검사한다.

### 승인 담당자 지정 (MANAGER)
`PUT /api/employee/{employeeId}/correction-approver` 본문 `{ "approverId": 9 }`. `null`이면 해제한다.
- MANAGER에게는 COMMUTE_APPROVER를, COMMUTE_APPROVER에게는 MANAGER를 지정한다. MEMBER 대상이나 본인 지정은 400 `INVALID_CORRECTION_APPROVER`.
- 대상 직원에게 PENDING 요청이 있으면 409 `PENDING_CORRECTION_EXISTS`. 처리 순서는 취소 → 변경 → 재신청이다.

### 월 마감 (MANAGER)

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/monthly-closings` | 마감된 월 목록 |
| GET | `/api/monthly-closings/{yearMonth}` | 마감 가능 여부·차단 사유·미해결 목록·발송 이력 |
| POST | `/api/monthly-closings` | 마감 (201). 커밋 후 같은 요청에서 발송을 시도한다 |

마감 요청: `{ "yearMonth": "2026-09" }`. 기능 도입 전 발송 월이면 `legacyResolution`과 `note`를 함께 보낸다.

```json
{ "yearMonth": "2026-08", "legacyResolution": "NO_CORRECTION_NEEDED", "note": "8월 발송본 확인, 정정 대상 없음" }
```

상태 응답 예 (`GET /api/monthly-closings/2026-09`):

```json
{
  "yearMonth": "2026-09",
  "rangeStart": "2026-08-31",
  "rangeEnd": "2026-09-30",
  "closable": false,
  "legacyDispatch": false,
  "blockers": ["UNCLOSED_COMMUTES", "PENDING_CORRECTIONS"],
  "unclosedCommutes": [
    { "commuteHistoryId": 41, "employee": { "employeeId": 3, "name": "홍길동", "employeeCode": "EMP0003" }, "workDate": "2026-09-02" }
  ],
  "pendingCorrections": [
    { "requestId": 7, "commuteHistoryId": 41, "requester": { "employeeId": 3, "name": "홍길동", "employeeCode": "EMP0003" }, "workDate": "2026-09-02" }
  ],
  "dispatches": []
}
```

마감 응답: `{ "closing": MonthlyClosingResponse, "dispatch"?: OverTimeReportDispatchResponse }`. 발송이 실패해도 마감은 유지되며 201이다. 결과는 `dispatch.status`로 확인한다.

`blockers` enum: `MONTH_NOT_ENDED`, `ALREADY_CLOSED`, `UNCLOSED_COMMUTES`, `PENDING_CORRECTIONS`, `DELIVERY_UNCERTAIN`, `PREVIOUS_LEGACY_MONTH_PENDING`.

### 발송·확정본 (MANAGER)
- `POST /api/overtime/report/dispatch/confirmation`: `DELIVERY_COMMITTED` 발송의 수신 여부를 운영자가 확인한다. 본문 `{ yearMonth, kind, outcome: DELIVERED|NOT_DELIVERED, note }`.
  - 409 `DISPATCH_NOT_UNCERTAIN`, 404 `DISPATCH_NOT_FOUND`.
- `GET /api/overtime/report/final-excel?yearMonth=&kind=ORIGINAL|CORRECTION`: 보관된 확정본을 그대로 내려준다.
  - 없으면 404 `REPORT_FILE_NOT_FOUND`.
  - 파일명은 `…초과근무보고서.xlsx` 또는 `…초과근무보고서_정정본.xlsx`.

## 4. 오류 코드와 권장 화면 메시지

| 코드 | HTTP | 권장 메시지 / 동작 |
| --- | --- | --- |
| `COMMUTE_END_WINDOW_EXPIRED` | 409 | 출근 후 24시간이 지나 일반 퇴근할 수 없습니다. 정정 신청을 해 주세요. |
| `COMMUTE_ALREADY_ENDED` | 409 | 최근 근무는 이미 퇴근 처리됐습니다. (근태 재조회) |
| `CLOSING_PERIOD_LOCKED` | 409 | 마감된 보고서에 포함된 기간이라 변경할 수 없습니다. |
| `REPORT_DELIVERY_UNCERTAIN` | 409 | 보고서 수신 확인 전이라 이 기간은 임시로 변경할 수 없습니다. |
| `COMMUTE_NOT_FOUND` | 404 | 근태 기록을 찾을 수 없습니다. |
| `CORRECTION_TARGET_DAY_OFF` | 400 | 연차 기록은 정정할 수 없습니다. |
| `CORRECTION_END_BEFORE_START` | 400 | 종료 시각은 출근 시각 이후여야 합니다. (시각 필드 오류) |
| `CORRECTION_END_IN_FUTURE` | 400 | 종료 시각은 현재 시각 이전이어야 합니다. (시각 필드 오류) |
| `CORRECTION_NO_CHANGE` | 400 | 현재 종료 시각과 같습니다. (시각 필드 오류) |
| `CORRECTION_OVERLAPS_NEXT_WORK` | 409 | 다음 근무의 출근 시각보다 늦을 수 없습니다. (재조회) |
| `CORRECTION_ALREADY_PENDING` | 409 | 이 기록에 승인 대기 중인 요청이 있습니다. (재조회) |
| `CORRECTION_VERSION_CONFLICT` | 409 | 기록이 변경됐습니다. 최신 내용을 확인해 주세요. (근태·요청 재조회, 반려 아님) |
| `CORRECTION_ALREADY_PROCESSED` | 409 | 이미 처리된 요청입니다. (재조회) |
| `CORRECTION_APPROVER_NOT_ASSIGNED` | 409 | 승인 담당자가 지정되지 않았습니다. 근태 관리자에게 문의하세요. |
| `CORRECTION_SELF_APPROVAL` | 403 | 본인 요청은 처리할 수 없습니다. |
| `CORRECTION_REQUEST_NOT_FOUND` | 404 | 요청을 찾을 수 없습니다. |
| `INVALID_CORRECTION_APPROVER` | 400 | 지정할 수 없는 승인 담당자입니다. |
| `PENDING_CORRECTION_EXISTS` | 409 | 승인 대기 중인 정정 요청을 먼저 처리해야 합니다. |
| `CLOSING_MONTH_NOT_ENDED` | 400 | 끝난 과거 월만 마감할 수 있습니다. |
| `MONTH_ALREADY_CLOSED` | 409 | 이미 마감된 월입니다. |
| `CLOSING_HAS_UNRESOLVED` | 409 | 미퇴근 또는 승인 대기 요청이 남아 있습니다. (상태 재조회) |
| `LEGACY_RESOLUTION_REQUIRED` | 409 | 기존 발송 월입니다. 정정 여부를 선택해 주세요. |
| `LEGACY_RESOLUTION_NOT_APPLICABLE` | 400 | 기존 발송 월이 아닙니다. |
| `CLOSING_PREVIOUS_LEGACY_MONTH_PENDING` | 409 | 집계 기간이 겹치는 이전 기존 발송 월을 먼저 정리해 주세요. |
| `DISPATCH_NOT_FOUND` | 404 | 발송 이력이 없습니다. |
| `DISPATCH_NOT_UNCERTAIN` | 409 | 수신 확인이 필요한 상태가 아닙니다. |
| `REPORT_FILE_NOT_FOUND` | 404 | 보관된 확정본이 없습니다. |
| `CSRF_ORIGIN_REJECTED` | 403 | 허용되지 않은 출처의 요청입니다. |

409 충돌 후에는 관련 Query를 무효화한다: 근태 월(인접 월 포함), 내 요청, 처리 목록, 마감 상태.

## 5. 서버 쓰기 규칙 (프론트엔드 참고)

- **잠금 순서**: 근태·정정·연차 쓰기는 소유 직원 행을 먼저 `SELECT … FOR UPDATE`로 잠근다. 월 마감·퇴사·담당자 지정은 전체 직원 행을 id 오름차순으로 잠근다. 따라서 마감 검사 직후 끼어드는 쓰기는 마감이 커밋된 뒤 실행되며 `CLOSING_PERIOD_LOCKED`로 거부된다.
- **버전**: 일반 퇴근과 정정 승인은 조건부 UPDATE에서 `version`을 1 올린다. 다른 기록의 퇴근은 대상 기록의 버전을 바꾸지 않는다.
- **대기 요청 1건**: DB UNIQUE(`pending_commute_history_id`). 처리·취소 시 NULL로 비워 재신청을 허용한다.
- **보호 기간**: 월 M의 `rangeStart`(M월 1일이 속한 주의 월요일) ~ M월 말. 예를 들어 2026-09 마감은 2026-08-31 ~ 2026-09-30을 보호한다.
- **CSRF**: 상태 변경 요청은 Origin/Referer가 같은 출처여야 한다. dev 프록시(5173)는 서버 설정으로 허용되므로 프론트엔드 변경은 필요 없다.
