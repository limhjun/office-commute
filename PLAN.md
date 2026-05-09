# PLAN

WORKS.md 항목별 상세 개발 계획. 항목을 시작할 때 본 문서에 추가/갱신한다.

---

## 예외 처리 정책 (확정)

| Layer | 종류 | 누가 던지나 | 핸들러 | HTTP | ErrorCode |
|---|---|---|---|---|---|
| L1 | DTO 입력 검증 | Bean Validation (`@Valid`) | `MethodArgumentNotValidException` 핸들러 | 400 | `VALIDATION_ERROR` + `fieldErrors` |
| L2 | 비즈니스 규칙 위반 | 커스텀 도메인 예외 (`*AlreadyExists`, `*NotFound`, `*NotMet`, ...) | 클래스별 `@ExceptionHandler` | 의미별 (400/404/409 등) | `*_ALREADY_EXISTS`, `*_NOT_FOUND`, ... |
| L3 | 도메인 불변식 방어선 | Java 기본 예외 (상황별 `IllegalArgumentException` / `NullPointerException` / `IllegalStateException`) | **핸들러 없음 → 일반 `Exception` 핸들러로 흡수** | 500 | `INTERNAL_SERVER_ERROR` |

핵심 원칙
- **L3는 사용자가 정상 경로로 닿지 않는 자리.** 닿으면 버그/회귀 시그널이며 5xx + 로깅으로 발견한다.
- `@ExceptionHandler(IllegalArgumentException.class)`는 **최종적으로 제거**한다 — 그래야 L3가 5xx로 빠지는 정책 의미가 산다.
- 상황별 Java 기본 예외 매핑:
  - blank / 형식 위반 / 음수·범위 위반 → `IllegalArgumentException`
  - null이면 안 되는 인자 → `NullPointerException` (`Objects.requireNonNull`)
  - 객체 상태가 호출 부적합 → `IllegalStateException`
- DTO `@Valid`가 1차 게이트(L1) — 사용자 입력 오류는 거의 모두 여기서 fieldErrors로 구체 메시지가 나간다.
- 비즈니스 규칙 위반(L2)은 "이미 등록됨", "팀 미배정 직원이 연차 신청", "이전 근무가 종료되지 않음" 등 *비즈니스 의미*가 있는 위반. 각각 의미를 가진 도메인 예외 + ErrorCode로 응답.

---

## 적용 단계 (영역별 점진 적용)

| Phase | 영역 | 핵심 변경 | IAE 핸들러 |
|---|---|---|---|
| 1 | Team | `TeamNameInvalidException`/`TeamPolicyInvalidException` 삭제 → IAE로 다운그레이드 | 유지 (브릿지) |
| 2 | Commute | `CommuteHistory`/`CommuteHistoryService`의 비즈니스 IAE/ISE → 도메인 예외 | 유지 |
| 3 | Annual Leave | `Employee.enrollAnnualLeave` / `AnnualLeaves` / `AnnualLeave` / `AnnualLeaveEnrollment`의 비즈니스 IAE/ISE → 도메인 예외 | 유지 |
| 4 | 글로벌 정리 | IAE 핸들러 제거 + 잔존 IAE 전수 검사 + (선택) `IllegalArgumentException`/`NullPointerException`/`IllegalStateException` 명시 핸들러로 5xx 분리 로깅 | **제거** |

각 Phase는 다음 사이클을 따른다:
1. 도메인 예외 신설 (Phase 2~3) 또는 기존 예외 정리 (Phase 1, 4)
2. throw 지점 교체
3. `GlobalExceptionHandler` 핸들러 + ErrorCode 추가/제거
4. 단위/컨트롤러/통합 테스트 갱신
5. `openapi.yml` 갱신
6. 좁은 Gradle 테스트 통과 확인 후 커밋
7. 다음 Phase 진입 시 `tasks.md`를 해당 Phase로 교체

---

## Phase 1 — Team 영역 정리

### 목표
`Team` 도메인의 커스텀 검증 예외 두 개를 정책(L3 = Java 기본 예외)에 맞게 다운그레이드한다.

### 작업
- `TeamNameInvalidException` 클래스 삭제
- `TeamPolicyInvalidException` 클래스 삭제
- `Team.java:43-48` 두 throw → `IllegalArgumentException`
- `GlobalExceptionHandler.handleTeamNameInvalid` / `handleTeamPolicyInvalid` 제거
- `TeamTest`의 단언을 `IllegalArgumentException`으로 교체
- `openapi.yml` — 해당 코드 노출 없음 확인 (없음)

### 상태 후
글로벌 IAE 핸들러는 *유지*한다. Team 검증은 일시적으로 IAE 핸들러를 통해 400으로 응답되며, Phase 4에서 핸들러 제거와 함께 5xx로 빠진다 (정책 적용 완료).

---

## Phase 2 — Commute 영역 비즈니스 예외 분리

### 목표
출퇴근 도메인의 *비즈니스 위반*을 IAE/ISE에서 의미 있는 도메인 예외로 분리한다.

### 대상
- `CommuteHistory.java:89` — "출근을 하지 않은 상태입니다." (IAE)
- `CommuteHistory.java:92` — "이미 퇴근을 했습니다." (IAE)
- `CommuteHistory.java:95` — "퇴근 시간이 출근 시간보다 이릅니다." (IAE)
- `CommuteHistoryService.java:62` — "이전 근무가 아직 종료되지 않았습니다." (ISE)

### 신설 도메인 예외 (잠정 — Phase 진입 시 확정)
- `CommuteNotStartedException` — 출근 기록 없음
- `CommuteAlreadyEndedException` — 이미 퇴근 처리됨
- `InvalidCommuteRangeException` — 출근/퇴근 시각 역전
- `PreviousCommuteNotEndedException` — 이전 일자 근무가 종료되지 않음

ErrorCode 후보: `COMMUTE_NOT_STARTED` 400, `COMMUTE_ALREADY_ENDED` 409, `INVALID_COMMUTE_RANGE` 400, `PREVIOUS_COMMUTE_NOT_ENDED` 409.

### 작업
- 도메인 예외 4종 신설
- throw 교체 + 호출부 import 정리
- `GlobalExceptionHandler` 핸들러 + ErrorCode 추가
- 단위(`CommuteHistoryTest`, `CommuteHistoryServiceTest`) / 컨트롤러 테스트 단언 갱신
- `openapi.yml`의 `/commute/start`, `/commute/end` 응답에 새 ErrorCode 추가

### 상태 후
출퇴근 영역의 모든 비즈니스 위반이 명시적 의미 코드로 응답. IAE 핸들러는 유지.

---

## Phase 3 — Annual Leave 영역 비즈니스 예외 분리

### 목표
연차 도메인의 비즈니스 위반을 도메인 예외로 분리한다.

### 대상
- `Employee.java:147` (ISE) — "팀이 배정되지 않은 직원은 연차를 신청할 수 없습니다."
- `Employee.java:153` (IAE) — "팀의 연차 등록 기준을 충족하지 못합니다." (= `AnnualLeaveEnrollment.java:33`과 동일 의미)
- `AnnualLeaveEnrollment.java:33` (IAE) — 위와 동일 의미 → 통합
- `AnnualLeaves.java:18` (IAE) — "이미 등록된 휴가입니다."
- `AnnualLeave.java:34` (IAE) — "(date)는 지난 날짜입니다."

### 신설 도메인 예외 (잠정)
- `EmployeeWithoutTeamException` — 팀 미배정 직원의 연차 신청 시도
- `AnnualLeaveCriteriaNotMetException` — 팀 연차 등록 기준 미충족
- `AnnualLeaveDuplicateException` — 동일 일자 중복 신청
- `AnnualLeavePastDateException` — 과거 일자 신청

ErrorCode 후보: `EMPLOYEE_WITHOUT_TEAM` 409, `ANNUAL_LEAVE_CRITERIA_NOT_MET` 400, `ANNUAL_LEAVE_DUPLICATE` 409, `ANNUAL_LEAVE_PAST_DATE` 400.

### 작업
- 도메인 예외 4종 신설
- throw 교체 (Employee 두 곳 + AnnualLeaveEnrollment + AnnualLeaves + AnnualLeave)
- `GlobalExceptionHandler` 핸들러 + ErrorCode 추가
- 단위/통합 테스트 단언 갱신
- `openapi.yml` 연차 관련 엔드포인트 응답 갱신

### 상태 후
비즈니스 IAE/ISE 잔재 없음. IAE 핸들러는 유지(다음 Phase에서 제거).

---

## Phase 4 — 글로벌 IAE 핸들러 제거 (정책 잠금)

### 목표
정책 의미 완성 — L3가 5xx로 빠지는 상태를 만든다.

### 작업
- `GlobalExceptionHandler.handleIllegalArgument` 메서드 제거
- 코드베이스 전수 검사: 잔존 IAE/NPE/ISE가 모두 (a) 값 검증 카테고리에 속하는지 확인
  - `Employee` 검증 메서드들 (blank/형식)
  - `WorkingMinutes` (음수)
  - `Employee.register`의 `Objects.requireNonNull`
  - 그 외 발견 시 적절히 분류
- 컨트롤러 레이어 시나리오 회귀 테스트 — 사용자 입력으로 IAE/NPE/ISE가 발생하는 경로가 없음을 확인 (있으면 그건 미정리된 (b) → Phase 추가 후 처리)
- (선택) `IllegalArgumentException`/`NullPointerException`/`IllegalStateException`을 명시적으로 별도 `@ExceptionHandler`로 분리해 ERROR 로그 + 마커 코드(`UNEXPECTED_DOMAIN_VIOLATION`) 부착 — 모니터링 신호 강화

### 상태 후
정책 100% 적용 완료. L3 위반은 5xx + 로그로 잡힌다.

---

## (선택) Phase 5 — 모니터링/관측 강화

학습 단계에서는 미실행 가능. 운영 진입 시 고려:
- L3 5xx 응답에 `X-Request-Id` 헤더 + 로그 동일 ID 포함 → 추적성
- L3 5xx 발생 시 슬랙/메일 알람 (운영 정책 결정 후)
