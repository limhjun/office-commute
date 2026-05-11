# PLAN

WORKS.md 항목별 상세 개발 계획. 항목을 시작할 때 본 문서에 추가/갱신한다.

---

## 예외 처리 정책 (확정 — 본 프로젝트 전체에 적용)

| Layer | 종류 | 누가 던지나 | 핸들러 | HTTP | ErrorCode |
|---|---|---|---|---|---|
| L1 | DTO 입력 검증 | Bean Validation (`@Valid`) | `MethodArgumentNotValidException` 핸들러 | 400 | `VALIDATION_ERROR` + `fieldErrors` |
| L2 | 비즈니스 규칙 위반 | 커스텀 도메인 예외 (`*AlreadyExists`, `*NotFound`, `*NotMet`, ...) | 클래스별 `@ExceptionHandler` | 의미별 (400/404/409 등) | `*_ALREADY_EXISTS`, `*_NOT_FOUND`, ... |
| L3 | 도메인 불변식 방어선 | Java 기본 예외 (상황별 `IllegalArgumentException` / `NullPointerException` / `IllegalStateException`) | `handleUnexpectedDomainViolation` (통합) | 500 | `UNEXPECTED_DOMAIN_VIOLATION` |

핵심 원칙
- L3는 사용자가 정상 경로로 닿지 않는 자리. 닿으면 버그/회귀 시그널 → 5xx + ERROR 로그로 발견한다.
- 새 비즈니스 규칙이 생기면 L2 도메인 예외를 신설한다 (`IllegalArgumentException`을 비즈니스 의미로 재사용 금지).

---

## 완료된 작업 (예외 처리 정책 점진 적용)

| Phase | Commit | 영역 | 핵심 변경 |
|---|---|---|---|
| 1 | `87e0233` | Team | `TeamNameInvalidException` / `TeamPolicyInvalidException` 삭제 → IAE로 다운그레이드 |
| 2 | `d517f28` | Commute | `CommuteNotStartedException`, `CommuteAlreadyEndedException`, `InvalidCommuteRangeException`, `PreviousCommuteNotEndedException` 신설 + `EmployeeNotFoundException` 재사용 |
| 3 | `ac6377d` | Annual Leave | `EmployeeWithoutTeamException`, `AnnualLeaveCriteriaNotMetException`, `AnnualLeaveDuplicateException`, `AnnualLeavePastDateException` 신설 + `EmployeeNotFoundException` 재사용 |
| 4 | `9b6b6af` | 글로벌 | IAE 핸들러 제거 → `handleUnexpectedDomainViolation`으로 IAE/ISE/NPE 통합 흡수 (5xx + ERROR 로그) |

도메인 예외 클래스 카탈로그 (현재 시점 기준):
- `auth/AuthenticationFailedException`, `auth/ForbiddenException`
- `domain/team/TeamAlreadyExistsException`, `TeamNotFoundException`
- `domain/employee/EmployeeAlreadyExistsException`, `EmployeeNotFoundException`
- `domain/commute/CommuteNotStartedException`, `CommuteAlreadyEndedException`, `InvalidCommuteRangeException`, `PreviousCommuteNotEndedException`
- `domain/annual_leave/EmployeeWithoutTeamException`, `AnnualLeaveCriteriaNotMetException`, `AnnualLeaveDuplicateException`, `AnnualLeavePastDateException`
- `global/exception/HolidayDataUnavailableException`

---

## 1-③ 출퇴근 시간 기록 (진단)

### 목표
README/`WORKS.md`의 "③ 출퇴근 시간 기록 — 직원 ID 기준으로 출근/퇴근 시간을 기록한다"를 1-①·1-② 라운드에서 정립한 컨벤션(ID 기반 응답, 3-layer validation, 도메인 예외)과 정합하도록 마무리한다.

### 현황 요약

엔드포인트 (`CommuteHistoryController`)
- `POST /commute` — 현재 로그인 직원의 출근 등록. 서버 시각 사용. 응답 `void`(200 OK).
- `PUT /commute` — 현재 로그인 직원의 퇴근 등록. 서버 시각 사용. 응답 `void`(200 OK).
- `GET /commute?yearMonth=YYYY-MM` — 월별 근무 시간 조회 (※ 본 항목 ③ 외, ④의 영역).

서비스 (`CommuteHistoryService`)
- `registerWorkStartTime` — 직원 존재 확인 → `validatePreviousWorkCompleted` (진행 중 근무 없음) → 새 `CommuteHistory` save.
- `registerWorkEndTime` — 직원 존재 확인 → 진행 중 근무 조회 → `endWork(workEndTime)` → save.
- `getEmployee`는 `EmployeeNotFoundException` 사용 (Phase 2 적용).
- `validatePreviousWorkCompleted`는 `PreviousCommuteNotEndedException` 사용 (Phase 2 적용).

도메인 (`CommuteHistory`)
- 필드: `commuteHistoryId, employeeId, workStartTime, workEndTime, workingMinutes, usingDayOff, workDate`.
- `workDate = workStartTime.toLocalDate()` — 자정 기준으로 일자 결정.
- `endWork`는 `CommuteNotStartedException` / `CommuteAlreadyEndedException` / `InvalidCommuteRangeException` 사용 (Phase 2 적용).
- `WorkingMinutes` 값 객체로 음수 방어.

스키마 (`V1__init.sql`)
- `commute_history(commute_history_id PK, employee_id, work_start_time, work_end_time, working_minutes NOT NULL, using_day_off NOT NULL, work_date NOT NULL)`.
- UNIQUE `(employee_id, work_date)` — 동일 직원 동일 일자 중복 등록 방지.

`openapi.yml`
- `POST /commute` — 200/400(`DUPLICATE_WORK` 등)/401/404/409(`PREVIOUS_COMMUTE_NOT_ENDED`).
- `PUT /commute` — 200/400(`COMMUTE_NOT_STARTED`/`INVALID_COMMUTE_RANGE`)/401/404/409(`COMMUTE_ALREADY_ENDED`).

테스트
- `CommuteHistoryTest` (도메인 단위), `WorkingMinutesTest`, `CommuteHistoryServiceTest`(Mock), `CommuteHistoryServiceConcurrencyTest`(통합), `CommuteHistoryRepositoryTest`.
- **`CommuteHistoryControllerTest` 부재** — 권한·HTTP 시나리오 검증 없음.

### 식별된 갭

| # | 항목 | 위치 | 문제 | 영향도 |
|---|---|---|---|---|
| G1 | `commute_history.employee_id` NULL 허용 | `V1__init.sql:28` | 도메인은 항상 채우지만 DB 보장 없음. CLAUDE.md "3-layer validation" 위반 | 컨벤션 정합 |
| G2 | 등록 응답이 `void` | `CommuteHistoryController.java:24,29` | 1-①/1-②에서 정립한 "ID-based 응답" 컨벤션과 어긋남. 클라이언트가 등록된 commute의 ID를 모름 → 후속 보정/취소 불가 | 컨벤션 정합 |
| G3 | `POST /commute` 응답 200 | `CommuteHistoryController.java:23` | 새 자원 생성이므로 표준상 201 Created. 1-② 직원 등록은 201로 통일했음 | 컨벤션 정합 |
| G4 | `CommuteHistoryControllerTest` 부재 | `src/test/.../controller/commute` 없음 | 권한(401/403), 응답 코드, 에러 매핑(409/404) 등 HTTP 컨트랙트가 단위 테스트로 검증되지 않음 | 회귀 안전성 |
| G5 | `work_start_time` NULL 허용 | `V1__init.sql:29` | 정상 레코드는 항상 시작 시각이 있음(연차 레코드도 자정 시각으로 채움). NOT NULL 가능 | 컨벤션 정합 (낮음) |
| G6 | 자정을 넘기는 야간 근무 처리 | `CommuteHistory.java:82-84` | `workDate = workStartTime.toLocalDate()` 단일 결정. 22시 출근 → 익일 06시 퇴근 시 시작일에만 묶임. 정책 결정 필요 | 운영 정책 (다음 라운드) |
| G7 | 점심시간/휴게시간 차감 | `CommuteHistory.endWork` | 단순 `Duration.between(start, end)`. 정책 결정 필요 | 운영 정책 (다음 라운드 또는 ⑧) |
| G8 | 임의 시각 보정 (관리자 기능) | API 없음 | 출근 누락 후 매니저가 보정 입력하는 흐름 없음. 별도 항목으로 분리 가능 | 별도 기능 (범위 외) |
| G9 | `ZonedDateTime` ↔ `DATETIME(6)` 시간대 매핑 | JPA 자동 매핑 | DB 컬럼은 시간대 비저장 — 인스턴스 시점에 UTC 변환되는지, 다중 타임존 클라이언트 가정이 있는지 확인 필요 | 운영 (확인 후 결정) |
| G10 | `getWorkDurationPerDate`는 ④ 영역 | `CommuteHistoryController.java:33-38` | 본 항목 ③ 범위 외. ④ 진단에서 처리 | 범위 외 |

### 후속 라운드 결정 필요 사항 (G1~G5)

1. **G2 응답 — POST만 ID 응답? PUT도 ID 응답?**
   - (a) POST만 `{ commuteHistoryId }` 반환, PUT은 그대로 200 void.
   - (b) POST는 `{ commuteHistoryId }`, PUT도 `{ commuteHistoryId }` (퇴근으로 갱신된 동일 자원의 ID 에코).
   - 권장: (a) — 1-② 패턴(등록은 ID 응답, 변경은 void)과 정합.

2. **G5 — `work_start_time` NOT NULL로 강화?**
   - 연차 레코드(`CommuteHistory(employeeId, annualLeaveDate)`)는 시작/끝을 자정으로 채움 → NOT NULL 가능.
   - 권장: NOT NULL 적용. `work_end_time`도 동일 검토 (단, 진행 중인 근무는 endTime이 NULL이어야 하므로 endTime은 NULL 유지).

### 변경 설계 (옵션 1 + 권장안 채택 시)

#### (1) 응답 DTO 신설 — `CommuteRegisterResponse`
```java
public record CommuteRegisterResponse(Long commuteHistoryId) {}
```

#### (2) 컨트롤러
- `POST /commute` → 201 Created + `CommuteRegisterResponse` body.
- `PUT /commute` → 200 OK void 유지.

#### (3) 서비스
- `registerWorkStartTime`이 저장된 `CommuteHistory.commuteHistoryId`를 반환하도록 변경.
- `registerWorkEndTime`은 변경 없음.

#### (4) Flyway V4 — `V4__commute_history_constraints.sql`
```sql
ALTER TABLE commute_history
    MODIFY employee_id BIGINT NOT NULL,
    MODIFY work_start_time DATETIME(6) NOT NULL;
-- work_end_time은 NULL 허용 유지 (진행 중인 근무 표현)
```
- 기존 데이터에 NULL row 없는지 사전 점검 필요.

#### (5) 엔티티 매핑 보강
```java
@Column(name = "employee_id", nullable = false) private Long employeeId;
@Column(name = "work_start_time", nullable = false) private ZonedDateTime workStartTime;
```

#### (6) 테스트 신설 — `CommuteHistoryControllerTest`
- `POST /commute` — 401(인증 없음), 201(성공 + body에 `commuteHistoryId` 포함), 409(`PREVIOUS_COMMUTE_NOT_ENDED`), 400(`DUPLICATE_WORK` — 동일 일자 재시도, race net).
- `PUT /commute` — 401, 200, 400(`COMMUTE_NOT_STARTED` / `INVALID_COMMUTE_RANGE`), 409(`COMMUTE_ALREADY_ENDED`).
- 기존 `CommuteHistoryServiceTest`의 `verify(repository).save(...)` 자리에 `given(...save).willReturn(...)`을 추가하여 ID 흐름 검증.

#### (7) `openapi.yml` 갱신
- `POST /commute`: 응답 코드 201로 변경, body 스키마 `CommuteRegisterResponse` 추가.
- `PUT /commute`: 변경 없음.

### 산출물 체크리스트 (잠정 — 결정 후 tasks.md로 옮김)
- [ ] `CommuteRegisterResponse` 신설
- [ ] `CommuteHistoryService.registerWorkStartTime` 반환값 변경
- [ ] `CommuteHistoryController.POST /commute` 201 + body
- [ ] `CommuteHistory` 엔티티 NOT NULL 어노테이션 보강 (employee_id, work_start_time)
- [ ] `V4__commute_history_constraints.sql` 추가
- [ ] mysql 프로필 V4 적용 검증
- [ ] `CommuteHistoryControllerTest` 신설
- [ ] 기존 `CommuteHistoryServiceTest` 보강 (save → ID 흐름)
- [ ] `openapi.yml` `POST /commute` 응답 갱신
- [ ] `WORKS.md` ③ 체크박스 [x]
