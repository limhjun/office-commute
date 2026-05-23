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
- `POST /commute` — 401(인증 없음), 201(성공 + body에 `commuteHistoryId` 포함), 409(`PREVIOUS_COMMUTE_NOT_ENDED` / `DUPLICATE_WORK` — 동일 일자 재시도, race net).
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

---

## 1-③-α DUPLICATE_WORK 409 리팩터

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 같은 직원이 출근 버튼을 연속/동시에 여러 번 눌러도 DB에는 같은 날 출근 기록이 1건만 저장되도록 보장하고, 나머지 요청은 `409 DUPLICATE_WORK`로 일관 응답한다. 기존 `400 DUPLICATE_WORK`(메시지 문자열 매칭)를 제거하고, `GlobalExceptionHandler.handleDataIntegrity`의 NPE 위험과 H2/MySQL 메시지 의존성을 함께 제거한다.

**Architecture:** 본 문서 상단의 예외 처리 정책(L2 — 비즈니스 규칙 위반은 도메인 예외 + 의미별 HTTP)에 정합. CLAUDE.md의 "사전 catch → 도메인 예외 throw, `DataIntegrityViolationException`은 레이스 안전망일 뿐" 규약을 그대로 적용. 새 `DuplicateWorkOnDateException`을 `domain/commute/`에 도입하고 `CommuteHistoryService.registerWorkStartTime`에 (1) 현재 직원 타임존 기준 `workDate` 산출, (2) `existsByEmployeeIdAndWorkDate`를 **미완료 근무 검증보다 먼저** 수행해 같은 날 재클릭/광클을 `DUPLICATE_WORK`로 분류, (3) 다른 날 미완료 근무는 기존 `PREVIOUS_COMMUTE_NOT_ENDED`로 유지, (4) `saveAndFlush()`를 `try/catch (DataIntegrityViolationException)`로 감싸 동시 요청 race를 DB 유니크 키로 최종 차단. catch에서는 Hibernate `ConstraintViolationException#getConstraintName()`이 `uk_commute_history_employee_date`일 때만 `DuplicateWorkOnDateException`으로 재변환하고, 다른 제약 위반은 원 예외를 다시 던진다. H2 테스트 DDL도 동일 이름을 쓰도록 `CommuteHistory`의 `@UniqueConstraint`에 name을 명시한다. `GlobalExceptionHandler`는 (a) 새 예외 → 409 핸들러 신설, (b) 기존 `handleDataIntegrity`는 문자열 분기 제거 + null-가드 + 500 fallback으로 단순화. `openapi.yml`은 `/commute` POST의 `DUPLICATE_WORK`를 400 → 409로 이동.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Flyway(스키마 변경 없음), JUnit 5 + Mockito + AssertJ, MockMvcTester. 이번 α 리팩터에서는 `POST /commute`의 `409 DUPLICATE_WORK` 최소 HTTP 계약 테스트 1건을 포함하고, 나머지 컨트롤러 시나리오는 1-③ 본 라운드에서 확장한다.

**1-③ 본 라운드와의 관계:** 본 리팩터를 먼저 머지하면, 후속 1-③ 라운드의 "변경 설계 (6) 테스트 신설" 항목은 `400(DUPLICATE_WORK ...)`이 아니라 `409(DUPLICATE_WORK ...)`로 검증해야 한다. 이번 α에서 중복 출근 409 케이스는 최소 검증하므로, 1-③ 본 라운드에서는 201 응답/권한/나머지 에러 매핑을 확장한다. 1-③ 마무리 시 산출물 체크리스트의 해당 줄을 동기화할 것.

---

### File Structure

**Create:**
- `src/main/java/com/company/officecommute/domain/commute/DuplicateWorkOnDateException.java` — 동일 `(employee_id, work_date)` 충돌을 나타내는 도메인 예외. 메시지에 충돌 날짜 포함.
- `src/test/java/com/company/officecommute/controller/commute/CommuteHistoryControllerTest.java` — `POST /commute` 중복 출근이 `409 DUPLICATE_WORK`로 매핑되는 최소 HTTP 계약 테스트.

**Modify:**
- `src/main/java/com/company/officecommute/repository/commute/CommuteHistoryRepository.java` — `existsByEmployeeIdAndWorkDate(Long, LocalDate)` 파생 쿼리 추가.
- `src/main/java/com/company/officecommute/domain/commute/CommuteHistory.java` — H2/Hibernate DDL에서도 동일 제약조건 이름을 쓰도록 `@UniqueConstraint(name = "uk_commute_history_employee_date", ...)` 지정.
- `src/main/java/com/company/officecommute/service/commute/CommuteHistoryService.java:37-45` — `registerWorkStartTime`에 사전 체크 + `saveAndFlush` + `try/catch` 안전망 추가.
- `src/main/java/com/company/officecommute/global/exception/GlobalExceptionHandler.java:177-189` — 새 예외용 409 핸들러 추가, 기존 `handleDataIntegrity`에서 문자열 분기 제거하고 null-가드 적용 후 500 fallback으로 변경.
- `openapi.yml:180-205` — `/commute` POST 응답의 `DUPLICATE_WORK`를 400 블록에서 409 블록으로 이동.
- `openapi.yml:359-364` — 공통 `BadRequest` 응답 설명에서 `DUPLICATE_WORK` 언급 제거.

**Test (modify or add):**
- `src/test/java/com/company/officecommute/repository/commute/CommuteHistoryRepositoryTest.java` — `existsByEmployeeIdAndWorkDate` 검증용 슬라이스 테스트 신규 추가.
- `src/test/java/com/company/officecommute/service/commute/CommuteHistoryServiceTest.java:50` — 사전 체크 + safety-net 단위 테스트 추가, 기존 `verify(repository).save(...)` → `saveAndFlush(...)` 변경.
- `src/test/java/com/company/officecommute/service/commute/CommuteHistoryServiceConcurrencyTest.java:138-145` — 퇴근 후 같은 날 재출근 테스트가 `DuplicateWorkOnDateException`을 기대하도록 수정.
- `src/test/java/com/company/officecommute/controller/commute/CommuteHistoryControllerTest.java` — 신규. `DuplicateWorkOnDateException` → HTTP 409 + `DUPLICATE_WORK` 검증.

**Flyway:** 변경 없음 (운영 DDL의 유니크 제약 `uk_commute_history_employee_date`는 이미 존재). `CommuteHistory` 엔티티의 제약 이름만 운영 DDL과 맞춘다. `data.sql`도 변경 없음 (컬럼 미변경).

---

### Task 1: 도메인 예외 신설 `DuplicateWorkOnDateException`

**Files:**
- Create: `src/main/java/com/company/officecommute/domain/commute/DuplicateWorkOnDateException.java`

- [ ] **Step 1: 도메인 예외 클래스 작성**

`src/main/java/com/company/officecommute/domain/commute/DuplicateWorkOnDateException.java`

```java
package com.company.officecommute.domain.commute;

import java.time.LocalDate;

public class DuplicateWorkOnDateException extends RuntimeException {

    public DuplicateWorkOnDateException(LocalDate workDate) {
        super("해당 일자에 이미 출근 기록이 존재합니다: " + workDate);
    }

    public DuplicateWorkOnDateException(LocalDate workDate, Throwable cause) {
        super("해당 일자에 이미 출근 기록이 존재합니다: " + workDate, cause);
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/company/officecommute/domain/commute/DuplicateWorkOnDateException.java
git commit -m "feat(commute): DuplicateWorkOnDateException 도메인 예외 추가"
```

---

### Task 2: Repository에 `existsByEmployeeIdAndWorkDate` 추가

**Files:**
- Modify: `src/main/java/com/company/officecommute/repository/commute/CommuteHistoryRepository.java`
- Modify: `src/main/java/com/company/officecommute/domain/commute/CommuteHistory.java`
- Test:   `src/test/java/com/company/officecommute/repository/commute/CommuteHistoryRepositoryTest.java`

- [ ] **Step 1: 실패하는 테스트 작성 (`@DataJpaTest` 슬라이스)**

`CommuteHistoryRepositoryTest`에 다음 import 보강(`java.time.LocalDate`)과 테스트 두 건을 추가한다.

```java
@Test
@DisplayName("existsByEmployeeIdAndWorkDate — 동일 일자 기록이 있으면 true")
void existsByEmployeeIdAndWorkDate_returnsTrue_whenRecordExists() {
    // given
    Long employeeId = 1L;
    ZonedDateTime start = ZonedDateTime.of(2026, 5, 23, 9, 0, 0, 0, ZoneId.of("Asia/Seoul"));
    commuteHistoryRepository.save(new CommuteHistory(null, employeeId, start, null, 0, ZoneId.of("Asia/Seoul")));

    // when
    boolean exists = commuteHistoryRepository.existsByEmployeeIdAndWorkDate(employeeId, LocalDate.of(2026, 5, 23));

    // then
    assertThat(exists).isTrue();
}

@Test
@DisplayName("existsByEmployeeIdAndWorkDate — 다른 일자만 있으면 false")
void existsByEmployeeIdAndWorkDate_returnsFalse_whenNoRecordOnThatDate() {
    // given
    Long employeeId = 1L;
    ZonedDateTime start = ZonedDateTime.of(2026, 5, 22, 9, 0, 0, 0, ZoneId.of("Asia/Seoul"));
    commuteHistoryRepository.save(new CommuteHistory(null, employeeId, start, null, 0, ZoneId.of("Asia/Seoul")));

    // when
    boolean exists = commuteHistoryRepository.existsByEmployeeIdAndWorkDate(employeeId, LocalDate.of(2026, 5, 23));

    // then
    assertThat(exists).isFalse();
}
```

- [ ] **Step 2: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.company.officecommute.repository.commute.CommuteHistoryRepositoryTest"`
Expected: COMPILE FAIL — `existsByEmployeeIdAndWorkDate` 심볼 없음.

- [ ] **Step 3: Repository 인터페이스에 메서드 추가**

`src/main/java/com/company/officecommute/repository/commute/CommuteHistoryRepository.java`

import 보강: `import java.time.LocalDate;`

인터페이스 본문(예: `findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc` 위)에 추가:
```java
boolean existsByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);
```

- [ ] **Step 4: 엔티티 유니크 제약 이름 명시**

`src/main/java/com/company/officecommute/domain/commute/CommuteHistory.java`

기존:
```java
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"employee_id", "work_date"})
})
```

교체:
```java
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_commute_history_employee_date", columnNames = {"employee_id", "work_date"})
})
```

목적: `saveAndFlush` race 안전망에서 Hibernate `ConstraintViolationException#getConstraintName()`으로 같은 날 중복 출근 제약을 식별한다. 운영 Flyway DDL은 이미 같은 이름을 사용하므로 DB 마이그레이션은 필요 없다.

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "com.company.officecommute.repository.commute.CommuteHistoryRepositoryTest"`
Expected: PASS (신규 2건 + 기존 테스트 모두 통과).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/company/officecommute/domain/commute/CommuteHistory.java \
        src/main/java/com/company/officecommute/repository/commute/CommuteHistoryRepository.java \
        src/test/java/com/company/officecommute/repository/commute/CommuteHistoryRepositoryTest.java
git commit -m "feat(commute): 중복 출근 식별용 파생 쿼리와 제약 이름 정렬"
```

---

### Task 3: 서비스 사전 체크 + saveAndFlush 안전망

**Files:**
- Modify: `src/main/java/com/company/officecommute/service/commute/CommuteHistoryService.java`
- Test:   `src/test/java/com/company/officecommute/service/commute/CommuteHistoryServiceTest.java`

- [ ] **Step 1: 실패하는 단위 테스트 추가**

`CommuteHistoryServiceTest`에 다음 import를 보강한다.
```java
import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
```

그리고 다음 테스트 세 건을 추가한다.
```java
@Test
@DisplayName("registerWorkStartTime — 같은 employee+workDate 기록이 있으면 미완료 근무 검증보다 먼저 DuplicateWorkOnDateException")
void registerWorkStartTime_throwsDuplicate_whenPriorRecordExistsOnSameDate() {
    // given
    BDDMockito.given(employeeRepository.findById(1L))
            .willReturn(Optional.of(employee));
    BDDMockito.given(commuteHistoryRepository.existsByEmployeeIdAndWorkDate(eq(1L), any(LocalDate.class)))
            .willReturn(true);

    // when / then
    assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(1L))
            .isInstanceOf(DuplicateWorkOnDateException.class)
            .hasMessageContaining("이미 출근 기록이 존재");

    then(commuteHistoryRepository).should(never())
            .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(1L);
    then(commuteHistoryRepository).should(never()).saveAndFlush(any(CommuteHistory.class));
}

@Test
@DisplayName("registerWorkStartTime — saveAndFlush의 중복 race를 Duplicate로 재던진다")
void registerWorkStartTime_translatesDataIntegrityViolation() {
    // given
    DataIntegrityViolationException violation = new DataIntegrityViolationException(
            "unique constraint",
            new ConstraintViolationException("unique constraint", null, "uk_commute_history_employee_date")
    );
    BDDMockito.given(employeeRepository.findById(1L))
            .willReturn(Optional.of(employee));
    BDDMockito.given(commuteHistoryRepository
                    .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(1L))
            .willReturn(Optional.empty());
    BDDMockito.given(commuteHistoryRepository.existsByEmployeeIdAndWorkDate(eq(1L), any(LocalDate.class)))
            .willReturn(false);
    BDDMockito.given(commuteHistoryRepository.saveAndFlush(any(CommuteHistory.class)))
            .willThrow(violation);

    // when / then
    assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(1L))
            .isInstanceOf(DuplicateWorkOnDateException.class)
            .hasCauseInstanceOf(DataIntegrityViolationException.class);
}

@Test
@DisplayName("registerWorkStartTime — 중복이 아닌 DataIntegrityViolation은 Duplicate로 오분류하지 않는다")
void registerWorkStartTime_doesNotTranslateNonDuplicateDataIntegrityViolation() {
    // given
    DataIntegrityViolationException violation = new DataIntegrityViolationException(
            "not duplicate",
            new ConstraintViolationException("not duplicate", null, "some_other_constraint")
    );
    BDDMockito.given(employeeRepository.findById(1L))
            .willReturn(Optional.of(employee));
    BDDMockito.given(commuteHistoryRepository.existsByEmployeeIdAndWorkDate(eq(1L), any(LocalDate.class)))
            .willReturn(false);
    BDDMockito.given(commuteHistoryRepository
                    .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(1L))
            .willReturn(Optional.empty());
    BDDMockito.given(commuteHistoryRepository.saveAndFlush(any(CommuteHistory.class)))
            .willThrow(violation);

    // when / then
    assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(1L))
            .isSameAs(violation);
}
```

또한 기존 다음 세 메서드의 verify 호출을 `save` → `saveAndFlush`로 교체한다.
- `testRegisterWorkStartTime`: `verify(commuteHistoryRepository).save(any(CommuteHistory.class));` → `verify(commuteHistoryRepository).saveAndFlush(any(CommuteHistory.class));`
- `seoulEmployee_workDate가_seoul_로컬일자로_계산된다`: `verify(commuteHistoryRepository).save(captor.capture());` → `verify(commuteHistoryRepository).saveAndFlush(captor.capture());`
- `laEmployee_같은_instant이라도_LA_로컬일자로_계산된다`: 동일하게 `save` → `saveAndFlush`.

(주의: `testRegisterWorkEndTime`은 `registerWorkEndTime` 경로이므로 변경 없음 — 그 경로는 본 리팩터 대상이 아님.)

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew test --tests "com.company.officecommute.service.commute.CommuteHistoryServiceTest"`
Expected: FAIL — 신규 3건은 미구현으로 실패, 기존 3건은 `saveAndFlush` verify 변경분 때문에 서비스가 아직 `save`를 호출해 실패.

- [ ] **Step 3: 서비스 구현 변경**

`src/main/java/com/company/officecommute/service/commute/CommuteHistoryService.java`

import 보강:
```java
import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.LocalDate;
```

클래스 필드에 제약 이름 상수를 추가하고, `registerWorkStartTime` 메서드를 다음으로 교체:
```java
private static final String UK_COMMUTE_HISTORY_EMPLOYEE_DATE = "uk_commute_history_employee_date";

@Transactional
public void registerWorkStartTime(Long employeeId) {
    Employee employee = getEmployee(employeeId);
    ZoneId employeeZone = employee.getZoneId();
    ZonedDateTime now = ZonedDateTime.now(clock.withZone(employeeZone));
    LocalDate workDate = now.toLocalDate();
    if (commuteHistoryRepository.existsByEmployeeIdAndWorkDate(employee.getEmployeeId(), workDate)) {
        throw new DuplicateWorkOnDateException(workDate);
    }
    validatePreviousWorkCompleted(employee.getEmployeeId());

    CommuteHistory newWork = new CommuteHistory(null, employee.getEmployeeId(), now, null, 0, employeeZone);
    try {
        commuteHistoryRepository.saveAndFlush(newWork);
    } catch (DataIntegrityViolationException e) {
        if (isDuplicateWorkConstraint(e)) {
            throw new DuplicateWorkOnDateException(workDate, e);
        }
        throw e;
    }
}

private boolean isDuplicateWorkConstraint(Throwable e) {
    Throwable current = e;
    while (current != null) {
        if (current instanceof ConstraintViolationException constraintViolationException) {
            return UK_COMMUTE_HISTORY_EMPLOYEE_DATE.equalsIgnoreCase(
                    constraintViolationException.getConstraintName());
        }
        current = current.getCause();
    }
    return false;
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.company.officecommute.service.commute.CommuteHistoryServiceTest"`
Expected: PASS (신규 3건 + 기존 4건 모두 통과).

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/company/officecommute/service/commute/CommuteHistoryService.java \
        src/test/java/com/company/officecommute/service/commute/CommuteHistoryServiceTest.java
git commit -m "feat(commute): 같은 일자 출근 사전 체크 + saveAndFlush 안전망"
```

---

### Task 4: 동일 일자 재출근/동시성 테스트 기대치 갱신

**Files:**
- Modify: `src/test/java/com/company/officecommute/service/commute/CommuteHistoryServiceConcurrencyTest.java`

- [ ] **Step 1: 퇴근 후 같은 날 재출근이 도메인 예외를 기대하도록 변경**

import 정리: `org.springframework.dao.DataIntegrityViolationException` 제거, 다음을 추가.
```java
import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
```

테스트 메서드를 다음으로 교체:
```java
@Test
@DisplayName("퇴근 후 같은 날 재출근시 DuplicateWorkOnDateException")
void sameDaySecondStartThrowsDuplicateWorkOnDate() {
    commuteHistoryService.registerWorkStartTime(testEmployeeId);
    commuteHistoryService.registerWorkEndTime(testEmployeeId);

    assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(testEmployeeId))
            .isInstanceOf(DuplicateWorkOnDateException.class)
            .hasMessageContaining("이미 출근 기록이 존재");
}
```

추가로 `testConcurrentRegisterWorkStartTime_H2DB`는 실패 개수만 세지 말고 실패 예외 타입도 수집하도록 보강한다. 이렇게 해야 실제 H2/Hibernate에서 `uk_commute_history_employee_date` 제약 이름이 잡히지 않아 `DataIntegrityViolationException`이 그대로 새는 회귀를 막을 수 있다.

import 추가:
```java
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
```

테스트 내부 변경:
```java
Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

for (int i = 0; i < threadCount; i++) {
    executor.execute(() -> {
        try {
            commuteHistoryService.registerWorkStartTime(employeeId);
            successCount.incrementAndGet();
        } catch (Exception e) {
            failures.add(e);
            failCount.incrementAndGet();
        } finally {
            latch.countDown();
        }
    });
}

latch.await();

List<CommuteHistory> histories = commuteHistoryRepository.findAll();
assertThat(histories).hasSize(1);
assertThat(successCount.get()).isEqualTo(1);
assertThat(failCount.get()).isEqualTo(threadCount - 1);
assertThat(failures)
        .hasSize(threadCount - 1)
        .allSatisfy(failure -> assertThat(failure).isInstanceOf(DuplicateWorkOnDateException.class));
```

- [ ] **Step 2: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.company.officecommute.service.commute.CommuteHistoryServiceConcurrencyTest"`
Expected: PASS (전체 4건).

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/company/officecommute/service/commute/CommuteHistoryServiceConcurrencyTest.java
git commit -m "test(commute): 동시성 테스트가 DuplicateWorkOnDateException 기대하도록 변경"
```

---

### Task 5: `GlobalExceptionHandler` 핸들러 추가 및 단순화

**Files:**
- Modify: `src/main/java/com/company/officecommute/global/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: 신규 핸들러 + 단순화된 `handleDataIntegrity` 적용**

import 보강:
```java
import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
```

`handleDataIntegrity` 메서드 바로 위에 다음 핸들러를 추가:
```java
@ResponseStatus(HttpStatus.CONFLICT)
@ExceptionHandler(DuplicateWorkOnDateException.class)
public ErrorResult handleDuplicateWorkOnDate(DuplicateWorkOnDateException e) {
    log.warn("Duplicate work on date: {}", e.getMessage());
    return new ErrorResult("DUPLICATE_WORK", e.getMessage());
}
```

기존 `handleDataIntegrity`(현재 line 177-189)를 다음으로 교체:
```java
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
@ExceptionHandler(DataIntegrityViolationException.class)
public ErrorResult handleDataIntegrity(DataIntegrityViolationException e) {
    Throwable rootCause = e.getRootCause();
    String detail = (rootCause != null) ? rootCause.getMessage() : e.getMessage();
    log.error("Unhandled data constraint violation — likely missing domain pre-check: {}", detail, e);
    return new ErrorResult("DATA_INTEGRITY_ERROR", "데이터 제약조건을 위반했습니다");
}
```

`Objects` import가 더 이상 쓰이지 않으면 제거한다 (`grep "Objects\." src/main/java/com/company/officecommute/global/exception/GlobalExceptionHandler.java` 로 확인).

- [ ] **Step 2: 전체 테스트 실행**

Run: `./gradlew test`
Expected: PASS — 본 리팩터 범위의 모든 테스트와 기존 테스트가 통과해야 한다. (실패 시 `Task 3`~`Task 4`의 verify 변경분이 누락된 곳을 우선 점검.)

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/company/officecommute/global/exception/GlobalExceptionHandler.java
git commit -m "refactor(global): DuplicateWorkOnDate 409 핸들러 분리, handleDataIntegrity NPE 가드 및 500 격상"
```

---

### Task 6: `POST /commute` 중복 출근 HTTP 계약 테스트

**Files:**
- Create: `src/test/java/com/company/officecommute/controller/commute/CommuteHistoryControllerTest.java`

- [ ] **Step 1: 컨트롤러 테스트 신설**

`src/test/java/com/company/officecommute/controller/commute/CommuteHistoryControllerTest.java`

```java
package com.company.officecommute.controller.commute;

import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.service.commute.CommuteHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@AutoConfigureMockMvc
class CommuteHistoryControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private CommuteHistoryService commuteHistoryService;

    @Test
    @DisplayName("POST /commute — 같은 날 중복 출근이면 409 DUPLICATE_WORK")
    void registerWorkStartTime_duplicateWorkReturns409() {
        doThrow(new DuplicateWorkOnDateException(LocalDate.of(2026, 5, 23)))
                .when(commuteHistoryService).registerWorkStartTime(1L);

        assertThat(mockMvcTester
                .post()
                .uri("/commute")
                .session(memberSession()))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                            "code": "DUPLICATE_WORK",
                            "message": "해당 일자에 이미 출근 기록이 존재합니다: 2026-05-23"
                        }
                        """);
    }

    private MockHttpSession memberSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentEmployeeId", 1L);
        session.setAttribute("currentRole", Role.MEMBER);
        return session;
    }
}
```

- [ ] **Step 2: 컨트롤러 테스트 실행**

Run: `./gradlew test --tests "com.company.officecommute.controller.commute.CommuteHistoryControllerTest"`
Expected: PASS — `DuplicateWorkOnDateException`이 실제 HTTP `409`와 `DUPLICATE_WORK`로 매핑됨.

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/company/officecommute/controller/commute/CommuteHistoryControllerTest.java
git commit -m "test(commute): 중복 출근은 409 DUPLICATE_WORK 응답"
```

---

### Task 7: `openapi.yml` 동기화

**Files:**
- Modify: `openapi.yml`

- [ ] **Step 1: `/commute` POST 응답 블록 수정**

`openapi.yml` `/commute` POST(line 180-205 근방) `responses:` 블록을 다음으로 교체:

```yaml
      responses:
        '200': { description: 기록됨. 응답 본문 없음. }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '404':
          description: 세션의 직원이 존재하지 않음 (EMPLOYEE_NOT_FOUND)
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResult' }
        '409':
          description: |
            상태/중복 충돌.
            - PREVIOUS_COMMUTE_NOT_ENDED: 이전 근무가 종료되지 않음
            - DUPLICATE_WORK: 같은 날 이미 출근 기록 존재
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResult' }
        '500':
          description: 예상치 못한 데이터 제약 위반 (DATA_INTEGRITY_ERROR)
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResult' }
```

- [ ] **Step 2: 공통 `BadRequest` 응답 설명에서 `DUPLICATE_WORK` 제거**

기존:
```yaml
    BadRequest:
      description: 잘못된 요청 (BAD_REQUEST / INVALID_JSON / DUPLICATE_WORK 등)
```
교체:
```yaml
    BadRequest:
      description: 잘못된 요청 (BAD_REQUEST / INVALID_JSON 등)
```

(`ErrorResult.code` description 안의 예시 목록 `DUPLICATE_WORK, DATA_INTEGRITY_ERROR`는 그대로 두어도 무방 — 두 코드 모두 여전히 발생함.)

- [ ] **Step 3: `openApiValidate` 실행**

Run: `./gradlew openApiValidate`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
git add openapi.yml
git commit -m "docs(openapi): /commute DUPLICATE_WORK를 409로 이동, DATA_INTEGRITY_ERROR 500 명시"
```

---

### Task 8: 전체 검증

**Files:** (변경 없음 — 검증만)

- [ ] **Step 1: 전체 빌드 + 테스트 + OpenAPI 검증**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL — 모든 단위/슬라이스/통합 테스트와 `openApiValidate` 통과.

- [ ] **Step 2: (선택) `mysql` 프로필 부트 스모크**

Docker MySQL이 가동 중이라면:

Run: `./gradlew bootRun --args='--spring.profiles.active=mysql'`
Expected: 정상 부팅. Flyway는 V1~V6만 적용(본 리팩터는 신규 마이그레이션 없음). 로그에 ERROR 없음.

종료 후:

- [ ] **Step 3: (선택) API 스모크 — 409 응답 확인**

서버 가동 상태에서:

Run: `./scripts/api_test.sh`
Expected: 출근/퇴근 시나리오 통과. 추가로 동일 일자 재출근 케이스를 curl로 직접 확인:

Run:
```bash
curl -i -b cookies.txt -X POST http://localhost:8080/commute
```
Expected: 첫 호출 후 같은 날 재호출 시 `HTTP/1.1 409 ` 와 본문 `{"code":"DUPLICATE_WORK", ...}`.

---

### Self-Review Checklist

**Spec coverage (코드 리뷰 지적 항목 매핑):**
1. *"이 케이스를 409 CONFLICT로"* → Task 5 핸들러 + Task 6 HTTP 계약 테스트 + Task 7 openapi `409`. ✅
2. *"식별은 메시지 문자열 대신 제약조건 이름 기반이나 서비스 계층의 사전 catch로 견고하게"* → Task 2 파생 쿼리 + 엔티티 제약 이름 정렬, Task 3 서비스 사전 체크 + `saveAndFlush` `try/catch` 안전망 + Hibernate 제약조건 이름 식별. ✅
3. *"null 가드 추가"* → Task 5의 `Throwable rootCause = e.getRootCause(); ... (rootCause != null) ? ...`. ✅
4. *"출근 버튼 광클 방지"* → Task 3에서 같은 날 중복 체크를 미완료 근무 검증보다 먼저 수행, Task 4 동시성 테스트로 DB 1건만 저장됨을 유지, Task 6에서 실패 요청의 HTTP 409 응답 검증. ✅

**Placeholder scan:** "TBD"/"TODO"/"비슷하게"/"적절한 에러 처리 추가" 등 표현 없음. 모든 코드 블록은 컴파일 가능한 완전한 형태. ✅

**Type / 이름 일관성:**
- `DuplicateWorkOnDateException` — Task 1 정의, Task 3 throw, Task 4/Task 6 import, Task 5 핸들러에서 동일 이름/패키지 사용.
- `existsByEmployeeIdAndWorkDate(Long, LocalDate)` — Task 2 정의, Task 3 호출. 시그니처 일관.
- `uk_commute_history_employee_date` — Flyway V1, Task 2 엔티티 `@UniqueConstraint`, Task 3 제약조건 이름 비교에서 동일 문자열 사용.
- `saveAndFlush` — Task 3 서비스 호출과 단위 테스트 `verify(...)`가 일치.
- 에러 코드 `DUPLICATE_WORK` — Task 5 핸들러, Task 6 HTTP 테스트, Task 7 openapi. 동일 문자열.

**CLAUDE.md / 본 plan.md 상단 정책 준수:**
- 도메인 예외 1개 = 1 의미 (L2). ✅
- 사전 체크 + 안전망 패턴. ✅
- `openapi.yml` 동기화. ✅
- Flyway DDL 미변경, `data.sql` 미변경. ✅
- `LocalDateTime` 미사용, 시간대 처리는 `ZonedDateTime`/`LocalDate`. ✅
- `Clock` 주입 그대로 사용 — 테스트가 시간 제어 가능. ✅

**1-③ 본 라운드와의 후속 동기화 (메모):** 1-③ 마무리 시 "변경 설계 (6) 테스트 신설"의 `400(DUPLICATE_WORK ...)` 줄을 `409(DUPLICATE_WORK ...)`로 갱신할 것.
