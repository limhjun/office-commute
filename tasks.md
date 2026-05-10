# Tasks — 1-③ G9 (c-3 다중 시간대 기반)

PLAN.md "1-③ 출퇴근 시간 기록" 중 G9 (c-3 시간대) 구현 단위.

## 진행 원칙
- 한 단계가 끝날 때까지 빌드/테스트는 항상 그린이어야 한다.
- 좁은 Gradle 테스트 → 그린 → TODO 체크 → 다음 항목.
- Phase 종료 시 전체 테스트 통과 확인 후 커밋.

---

## A. 인프라
- [x] **A1.** `config/TimeConfig.java` 신설 — `Clock` 빈 (`Clock.systemUTC()`).

## B. 스키마 (Flyway)
- [x] **B1.** `V4__commute_timezone_support.sql` 추가
  - `employee.timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul'`
  - `commute_history.work_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul'`

## C. 도메인 — `Employee`
- [x] **C1.** `timezone` 필드 + `@Column(nullable=false)` 어노테이션
- [x] **C2.** 9-arg → 10-arg 생성자, 정적 팩토리 `register(...)` 시그니처 확장
- [x] **C3.** `timezone` 검증 (blank → IAE — 정책 부합)
- [x] **C4.** `getZoneId()` 헬퍼

## D. DTO — Employee
- [x] **D1.** `EmployeeSaveRequest.timezone` 추가 (옵셔널, null 허용)
- [x] **D2.** `EmployeeFindResponse.timezone` 추가 + `from(Employee)` 매핑

## E. 서비스 — `EmployeeService.registerEmployee`
- [x] **E1.** `request.timezone()` null 시 `'Asia/Seoul'` 기본값 적용
- [x] **E2.** `Employee.register(...)` 호출에 timezone 전달

## F. 도메인 — `CommuteHistory`
- [x] **F1.** `workZone` 필드 + `@Column(name="work_zone", nullable=false)`
- [x] **F2.** 생성자 확장 — workZone 인자 (모든 4종 생성자: 1-arg, 2-arg, 5-arg, 6-arg, 7-arg)
- [x] **F3.** `workDate` 계산을 `workStartTime.withZoneSameInstant(workZone).toLocalDate()`로 변경
- [x] **F4.** `getWorkZoneId()` 헬퍼

## G. 서비스 — `CommuteHistoryService`
- [x] **G1.** `Clock` 의존성 주입
- [x] **G2.** `registerWorkStartTime` — 직원 zone 기반 `ZonedDateTime.now(clock.withZone(...))` 사용 + workZone 저장
- [x] **G3.** `registerWorkEndTime` 시그니처 변경 — 외부 시각 인자 제거. 직원의 진행 중 commute의 workZone 기준 `now()` 생성
- [x] **G4.** `getWorkDurationPerDate`의 zone 의존 부분 — 본 라운드 범위 외, 추후 ④에서 처리 (현 동작 유지)

## H. 컨트롤러 — `CommuteHistoryController`
- [x] **H1.** `registerWorkEndTime` — `ZonedDateTime.now()` 제거, `service.registerWorkEndTime(employeeId)` 단일 호출

## I. 서비스 — `AnnualLeaveService`
- [x] **I1.** 연차 기반 CommuteHistory 생성 시 employee.zoneId 전달

## J. 픽스처 / 시드 데이터
- [x] **J1.** `EmployeeBuilder.build()` — timezone 기본값 `'Asia/Seoul'` 설정
- [x] **J2.** `data.sql` — INSERT employee에 timezone 추가

## K. openapi.yml
- [x] **K1.** `EmployeeSaveRequest` 스키마에 `timezone` 옵셔널 필드
- [x] **K2.** `EmployeeFindResponse` 스키마에 `timezone` 필드

## L. 테스트
- [x] **L1.** 컴파일 깨진 곳 일괄 수정 (Employee 생성자/팩토리 시그니처 변경 영향)
- [x] **L2.** `EmployeeServiceTest` — timezone 기본값 / 명시 입력 케이스 추가
- [x] **L3.** `CommuteHistoryServiceTest` — `Clock` mock 주입, 직원 zone에 따라 workDate가 다른 케이스 (한국 직원 vs LA 직원)
- [x] **L4.** 기존 통합/REST Docs 테스트 컴파일 그린 유지

## M. 검증
- [x] **M1.** `./gradlew test --tests "com.company.officecommute.domain.*"` 그린
- [x] **M2.** `./gradlew test --tests "com.company.officecommute.service.*"` 그린
- [x] **M3.** `./gradlew test --tests "com.company.officecommute.controller.*"` 그린
- [x] **M4.** `./gradlew test` 전체 그린

## N. 마무리
- [x] **N1.** 변경사항 커밋 — `feat: introduce per-employee timezone with commute zone snapshot (c-3)`
