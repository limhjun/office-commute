# Tasks — Phase 1 (Team 영역 정리)

PLAN.md의 Phase 1 실행 단위. 각 항목 완료 시 `[x]`로 갱신.

## 진행 원칙
- 한 단계가 끝날 때까지 빌드/테스트는 항상 그린이어야 한다.
- 좁은 Gradle 테스트 → 그린 → TODO 체크 → 다음 항목.
- Phase 종료 시 전체 테스트 통과 확인 후 커밋.

---

## A. 도메인 예외 클래스 정리
- [x] **A1.** `domain/team/TeamNameInvalidException.java` 삭제
- [x] **A2.** `domain/team/TeamPolicyInvalidException.java` 삭제

## B. 도메인 (`Team.java`)
- [x] **B1.** `Team.java:43-44` — blank name → `IllegalArgumentException("팀 이름은 비어있을 수 없습니다.")`
- [x] **B2.** `Team.java:46-48` — `annualLeaveCriteria < 0` → `IllegalArgumentException("팀 연차 등록 기준은 0 이상이어야 합니다.")`

## C. `GlobalExceptionHandler`
- [x] **C1.** `handleTeamNameInvalid` 메서드 + import 제거 (line 8, 84-88)
- [x] **C2.** `handleTeamPolicyInvalid` 메서드 + import 제거 (line 10, 90-95)

## D. 테스트
- [x] **D1.** `TeamTest:22` — `TeamNameInvalidException` 단언을 `IllegalArgumentException`으로 교체
- [x] **D2.** `TeamTest:29` — `TeamPolicyInvalidException` 단언을 `IllegalArgumentException`으로 교체

## E. 검증
- [x] **E1.** `./gradlew test --tests "com.company.officecommute.domain.team.*"` 그린
- [x] **E2.** `./gradlew test --tests "com.company.officecommute.service.team.*"` 그린
- [x] **E3.** `./gradlew test --tests "com.company.officecommute.controller.team.*"` 그린
- [x] **E4.** `./gradlew test` 전체 그린

## F. 마무리
- [ ] **F1.** 변경사항 커밋 — `refactor: downgrade Team validation to IllegalArgumentException`
