# Tasks — Phase 3 (Annual Leave 영역 비즈니스 예외 분리)

PLAN.md의 Phase 3 실행 단위. 각 항목 완료 시 `[x]`로 갱신.

## 진행 원칙
- 한 단계가 끝날 때까지 빌드/테스트는 항상 그린이어야 한다.
- 좁은 Gradle 테스트 → 그린 → TODO 체크 → 다음 항목.
- Phase 종료 시 전체 테스트 통과 확인 후 커밋.

## 신설 도메인 예외 4종 (`domain/annual_leave/`)
- `EmployeeWithoutTeamException` — 팀 미배정 직원의 연차 신청
- `AnnualLeaveCriteriaNotMetException` — 팀 연차 등록 기준 미충족
- `AnnualLeaveDuplicateException` — 동일 일자 중복 신청
- `AnnualLeavePastDateException` — 과거 일자 신청

ErrorCode → HTTP:
- `EMPLOYEE_WITHOUT_TEAM` → 409
- `ANNUAL_LEAVE_CRITERIA_NOT_MET` → 400
- `ANNUAL_LEAVE_DUPLICATE` → 409
- `ANNUAL_LEAVE_PAST_DATE` → 400

추가: `AnnualLeaveService.java:42` "존재하지 않는 직원입니다." → 기존 `EmployeeNotFoundException` 재사용.

---

## A. 도메인 예외 클래스 신설
- [x] **A1.** `domain/annual_leave/EmployeeWithoutTeamException.java`
- [x] **A2.** `domain/annual_leave/AnnualLeaveCriteriaNotMetException.java`
- [x] **A3.** `domain/annual_leave/AnnualLeaveDuplicateException.java`
- [x] **A4.** `domain/annual_leave/AnnualLeavePastDateException.java`

## B. throw 지점 교체
- [x] **B1.** `Employee.java:147` ISE → `EmployeeWithoutTeamException`
- [x] **B2.** `Employee.java:153` IAE → `AnnualLeaveCriteriaNotMetException`
- [x] **B3.** `AnnualLeaveEnrollment.java:33` IAE → `AnnualLeaveCriteriaNotMetException`
- [x] **B4.** `AnnualLeaves.java:18` IAE → `AnnualLeaveDuplicateException`
- [x] **B5.** `AnnualLeave.java:34` IAE → `AnnualLeavePastDateException`
- [x] **B6.** `AnnualLeaveService.java:42` IAE → `EmployeeNotFoundException`

## C. `GlobalExceptionHandler` 핸들러 추가
- [x] **C1.** `handleEmployeeWithoutTeam` — 409 / `EMPLOYEE_WITHOUT_TEAM`
- [x] **C2.** `handleAnnualLeaveCriteriaNotMet` — 400 / `ANNUAL_LEAVE_CRITERIA_NOT_MET`
- [x] **C3.** `handleAnnualLeaveDuplicate` — 409 / `ANNUAL_LEAVE_DUPLICATE`
- [x] **C4.** `handleAnnualLeavePastDate` — 400 / `ANNUAL_LEAVE_PAST_DATE`

## D. 테스트 갱신
- [x] **D1.** `AnnualLeaveTest:16-17` — `assertThatIllegalArgumentException` → `AnnualLeavePastDateException`
- [x] **D2.** `AnnualLeavesTest:30-31` — `assertThatIllegalArgumentException` → `AnnualLeaveDuplicateException`
- [x] **D3.** `AnnualLeavesTest:43-44` — `assertThatIllegalArgumentException` → `AnnualLeaveDuplicateException`
- [x] **D4.** `AnnualLeaveEnrollmentTest:36-37` — `assertThatIllegalArgumentException` → `AnnualLeaveCriteriaNotMetException`

## E. `openapi.yml` 갱신
- [x] **E1.** `/annual-leave` POST 응답 — `400 ANNUAL_LEAVE_CRITERIA_NOT_MET / ANNUAL_LEAVE_PAST_DATE`, `409 ANNUAL_LEAVE_DUPLICATE / EMPLOYEE_WITHOUT_TEAM`, `404 EMPLOYEE_NOT_FOUND` 추가

## F. 검증
- [x] **F1.** `./gradlew test --tests "com.company.officecommute.domain.annual_leave.*"` 그린
- [x] **F2.** `./gradlew test --tests "com.company.officecommute.service.annual_leave.*"` 그린
- [x] **F3.** `./gradlew test --tests "com.company.officecommute.domain.employee.*"` 그린 (Employee.java 변경 영향)
- [x] **F4.** `./gradlew test` 전체 그린

## G. 마무리
- [x] **G1.** 변경사항 커밋 — `refactor: split annual leave business errors into domain exceptions`
