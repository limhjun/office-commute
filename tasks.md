# Tasks — Phase 4 (글로벌 IAE 핸들러 제거 / 정책 잠금)

PLAN.md의 Phase 4 실행 단위. 각 항목 완료 시 `[x]`로 갱신.

## 진행 원칙
- 한 단계가 끝날 때까지 빌드/테스트는 항상 그린이어야 한다.
- 좁은 Gradle 테스트 → 그린 → TODO 체크 → 다음 항목.
- Phase 종료 시 전체 테스트 통과 확인 후 커밋.

## 잔존 IAE/ISE/NPE 분류 결과 (사전 점검)

| 위치 | 종류 | 카테고리 | 정책 부합 여부 |
|---|---|---|---|
| `Team.java:44,47` | IAE | (a) 값 검증 | ✅ |
| `WorkingMinutes.java:11` | IAE | (a) 값 검증 (음수 차단) | ✅ |
| `Employee.java:103-105` | NPE (`Objects.requireNonNull`) | (a) null 체크 | ✅ |
| `Employee.java:113,120,127,131,138` | IAE | (a) 값 검증 (blank/형식) | ✅ |
| `ApiConvertor.java:142` | ISE | (c) 인프라/외부 API 응답 이상 | 정책 외 — 별도 정리 가능 |

→ **모두 사용자 정상 경로에서 닿지 않음.** 핸들러 제거 시 5xx로 빠짐(정책 부합).

---

## A. 글로벌 IAE 핸들러 제거
- [x] **A1.** `GlobalExceptionHandler.handleIllegalArgument` 메서드 제거

## B. (선택) IAE/NPE/ISE 명시 핸들러 — 모니터링 시그널 강화
- [x] **B1.** `handleUnexpectedDomainViolation` 신설 — `IllegalArgumentException` + `IllegalStateException` + `NullPointerException` 흡수, 500 응답 + `UNEXPECTED_DOMAIN_VIOLATION` 코드 + ERROR 로그 + stack trace
- [x] **B2.** 응답 메시지는 일반화 — "내부 도메인 검증에 실패했습니다." (사용자 노출 정보 최소화)

## C. 컨트롤러/서비스 흐름 회귀 검증
- [x] **C1.** 사용자 입력 시나리오에서 IAE/NPE/ISE가 5xx로 빠지는 경로가 없는지 — 컨트롤러 테스트 그대로 그린

## D. 검증
- [x] **D1.** `./gradlew test` 전체 그린

## E. 마무리
- [x] **E1.** 변경사항 커밋 — `refactor: remove generic IllegalArgumentException handler, lock exception policy`
