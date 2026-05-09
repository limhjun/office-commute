# Tasks — Phase 2 (Commute 영역 비즈니스 예외 분리)

PLAN.md의 Phase 2 실행 단위. 각 항목 완료 시 `[x]`로 갱신.

## 진행 원칙
- 한 단계가 끝날 때까지 빌드/테스트는 항상 그린이어야 한다.
- 좁은 Gradle 테스트 → 그린 → TODO 체크 → 다음 항목.
- Phase 종료 시 전체 테스트 통과 확인 후 커밋.

## 신설 도메인 예외 4종 (`domain/commute/`)
- `CommuteNotStartedException` — 출근 기록 없음 (`POST end` 시도 등)
- `CommuteAlreadyEndedException` — 이미 퇴근 처리됨
- `InvalidCommuteRangeException` — 출근/퇴근 시각 역전
- `PreviousCommuteNotEndedException` — 이전 근무가 종료되지 않음

ErrorCode → HTTP:
- `COMMUTE_NOT_STARTED` → 400
- `COMMUTE_ALREADY_ENDED` → 409
- `INVALID_COMMUTE_RANGE` → 400
- `PREVIOUS_COMMUTE_NOT_ENDED` → 409

추가: `CommuteHistoryService.java:55` "존재하지 않는 직원입니다." → 기존 `EmployeeNotFoundException` 재사용.

---

## A. 도메인 예외 클래스 신설
- [x] **A1.** `domain/commute/CommuteNotStartedException.java` 신설
- [x] **A2.** `domain/commute/CommuteAlreadyEndedException.java` 신설
- [x] **A3.** `domain/commute/InvalidCommuteRangeException.java` 신설
- [x] **A4.** `domain/commute/PreviousCommuteNotEndedException.java` 신설

## B. throw 지점 교체
- [x] **B1.** `CommuteHistory.java:89` IAE → `CommuteNotStartedException`
- [x] **B2.** `CommuteHistory.java:92` IAE → `CommuteAlreadyEndedException`
- [x] **B3.** `CommuteHistory.java:95` IAE → `InvalidCommuteRangeException`
- [x] **B4.** `CommuteHistoryService.java:62` ISE → `PreviousCommuteNotEndedException`
- [x] **B5.** `CommuteHistoryService.java:69` IAE → `CommuteNotStartedException`
- [x] **B6.** `CommuteHistoryService.java:55` IAE → `EmployeeNotFoundException` (재사용)

## C. `GlobalExceptionHandler` 핸들러 추가
- [x] **C1.** `handleCommuteNotStarted` — 400 / `COMMUTE_NOT_STARTED`
- [x] **C2.** `handleCommuteAlreadyEnded` — 409 / `COMMUTE_ALREADY_ENDED`
- [x] **C3.** `handleInvalidCommuteRange` — 400 / `INVALID_COMMUTE_RANGE`
- [x] **C4.** `handlePreviousCommuteNotEnded` — 409 / `PREVIOUS_COMMUTE_NOT_ENDED`

## D. 테스트 갱신
- [x] **D1.** `CommuteHistoryTest:32-33` — IAE → `CommuteNotStartedException`
- [x] **D2.** `CommuteHistoryTest:43-44` — IAE → `CommuteAlreadyEndedException`
- [x] **D3.** `CommuteHistoryTest:61-62` — IAE → `InvalidCommuteRangeException`
- [x] **D4.** `CommuteHistoryServiceConcurrencyTest:108-109` — ISE → `PreviousCommuteNotEndedException`

## E. `openapi.yml` 갱신
- [x] **E1.** `/commute` POST 응답 — `409 PREVIOUS_COMMUTE_NOT_ENDED`, `404 EMPLOYEE_NOT_FOUND` 추가
- [x] **E2.** `/commute` PUT 응답 — `400 COMMUTE_NOT_STARTED / INVALID_COMMUTE_RANGE`, `409 COMMUTE_ALREADY_ENDED`, `404 EMPLOYEE_NOT_FOUND` 추가

## F. 검증
- [x] **F1.** `./gradlew test --tests "com.company.officecommute.domain.commute.*"` 그린
- [x] **F2.** `./gradlew test --tests "com.company.officecommute.service.commute.*"` 그린
- [x] **F3.** `./gradlew test` 전체 그린

## G. 마무리
- [x] **G1.** 변경사항 커밋 — `refactor: split commute business errors into domain exceptions`
