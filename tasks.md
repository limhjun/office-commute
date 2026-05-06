# Tasks

PLAN.md를 실행 단위로 쪼갠 체크리스트. 작업이 끝난 항목은 `[x]`로 표시한다.

## 진행 원칙
- 실서비스(HR 플랫폼) 품질 기준으로 작업한다. "학습용이라 단순하게"는 금지.
- 점진적으로 진행한다. 한 번에 큰 폭의 리팩터링/마이그레이션을 하지 않는다.
- **테스트는 꼼꼼하게**: 도메인/서비스/컨트롤러/REST Docs 각 레이어에서 빠짐없이 갱신·추가한다. 동시성·예외·null·경계 케이스를 명시적으로 다룬다.
- 변경마다 가장 좁은 Gradle 테스트 태스크로 즉시 확인한다.

---

## 1-① 팀 등록 및 조회

### A. 요청/응답 DTO
- [x] **A1.** `TeamRegisterRequest`에 `String managerName` 필드 추가 (옵셔널, `@NotBlank` 미적용)
- [x] **A2.** `TeamFindResponse`에 `Long teamId` 필드 추가
- [x] **A3.** `TeamFindResponse.from(Team)` 매핑에 `team.getTeamId()` 반영

### B. 도메인 (`Team` 엔티티)
- [x] **B1.** `name` 필드에 `@Column(nullable = false, unique = true)` 적용
- [x] **B2.** `managerName`은 nullable 유지 (어노테이션 생략 또는 명시적 nullable=true)
- [x] **B3.** 등록 흐름은 정적 팩토리 `Team.register(name, managerName)` 사용 — blank managerName은 null로 정규화
- [x] **B4.** 기존 생성자 오버로딩은 테스트 픽스처에서 사용 중이라 단기 유지. 중기적으로 테스트를 `Team.register(...)` / `Teams` 픽스처로 통일 후 public 생성자 제거 검토 (별도 리팩터링 작업)

### C. 서비스 (`TeamService`)
- [x] **C1.** `registerTeam`이 `Team.register(name, managerName)` 사용 (정적 팩토리에서 blank → null 정규화 처리)
- [x] **C2.** 사전 `findByName` 중복 검사 유지 — `TeamAlreadyExistsException` throw
- [x] **C3.** `DataIntegrityViolationException` catch → `TeamAlreadyExistsException` 변환 (동시성 안전망)
- [x] **C4.** `findTeam`은 `teamRepository.findAll()` 그대로 사용
- [x] **C5.** `TeamAlreadyExistsException` 도메인 예외 신규 추가 (`domain/team/`)
- [x] **C6.** `GlobalExceptionHandler`에 핸들러 추가 → 409 Conflict + `TEAM_ALREADY_EXISTS` 코드

### D. 리포지토리 (`TeamRepository`)
- [x] **D1.** `findTeam()` JPQL 메서드 삭제 (데드 코드)
- [x] **D2.** `findByName` 시그니처 유지 확인

### E. 스키마 / Flyway
- [x] **E1.** `src/main/resources/db/migration/V2__team_constraints.sql` 추가
  - `team.name` NOT NULL
  - `team.name` UNIQUE (`uk_team_name`)
  - `manager_name`은 변경 없음 (NULL 허용 유지)
- [x] **E2.** mysql 프로필에서 마이그레이션 정상 적용 확인
  - 로컬 MySQL 8.0(docker)에 V1+V2 fresh 적용 성공 (flyway_schema_history.success=1)
  - `team.name` NOT NULL + `uk_team_name` UNIQUE 인덱스 생성, `manager_name`은 nullable 유지 확인
  - 주의: 기존 운영 데이터에 `name = NULL`인 row가 있으면 MODIFY 실패. 운영 적용 전 데이터 점검 필요

### F. 컨트롤러 (`TeamController`)
- [x] **F1.** 시그니처 변경 없음(요청/응답 DTO 변경에만 의존) — 빌드 통과 확인

### G. 테스트
- [x] **G1.** `TeamServiceTest` — `managerName` 포함 등록 검증
- [x] **G2.** `TeamServiceTest` — `managerName` null 등록 허용 검증
- [x] **G3.** `TeamServiceTest` — 중복 이름 등록 시 `TeamAlreadyExistsException`
- [x] **G4.** `TeamServiceTest` — `DataIntegrityViolationException` → `TeamAlreadyExistsException` 변환 단위 테스트
- [x] **G5.** `TeamServiceTest` — 조회 결과에 `teamId` / `managerName` 매핑 검증
- [x] **G6.** `TeamControllerTest` — 등록 권한(401/403/200), 매니저 누락/빈 문자열 허용, 팀 이름 빈 문자열 400, 중복 시 409 등 입력·충돌 케이스 전반
- [x] **G7.** `TeamControllerTest` — 조회 응답에 `teamId`/`name`/`managerName`(null 포함)/`memberCount` 모두 검증, 빈 목록 / 비인증 401
- [x] **G8.** `TeamControllerDocsTest` — REST Docs 요청에 `managerName` 옵셔널, 응답에 `teamId` 필드 추가
- [x] **G9.** 픽스처(`Teams`)는 기존 4-arg / 5-arg 생성자 사용으로 호환 — 변경 불필요

### H. 검증
- [x] **H1.** `./gradlew test --tests "com.company.officecommute.service.team.*"` 통과 (5/5)
- [x] **H2.** `./gradlew test --tests "com.company.officecommute.controller.team.*"` 통과 (10/10)
- [x] **H3.** `./gradlew test --tests "com.company.officecommute.docs.TeamControllerDocsTest"` 통과 (2/2)
- [x] **H4.** `./gradlew build` 전체 빌드 통과 (총 128 tests / asciidoctor / bootJar 모두 성공)
- [ ] **H5.** REST Docs 산출물 확인 (`build/docs/asciidoc/index.html`) — 사용자 브라우저 확인 필요

### I. 마무리
- [x] **I1.** `WORKS.md`의 1-① 항목 체크박스 `[x]`로 갱신
- [x] **I2.** 1-② 항목 시작 시 PLAN/tasks 섹션 추가

---

## 1-② 직원 등록 및 조회

### A. 요청/응답 DTO
- [x] **A1.** `EmployeeSaveRequest`에 `Long teamId` 옵셔널 필드 추가
- [x] **A2.** `EmployeeRegisterResponse(Long employeeId)` 신규
- [x] **A3.** `EmployeeFindResponse` 재설계: `employeeId`/`teamId`/`teamName`/`name`/`role`/`birthday`/`workStartDate` (날짜는 `LocalDate` 그대로 — Jackson ISO-8601)
- [x] **A4.** `EmployeeChangeTeamRequest(Long teamId)` 신규
- [x] **A5.** `EmployeeUpdateTeamNameRequest` 삭제 (F+H 그룹과 함께)

### B. 도메인 (`Employee`)
- [x] **B1.** 정적 팩토리 `Employee.register(name, role, birthday, workStartDate, employeeCode, email, encodedPassword, team)` 추가
- [x] **B2.** 4-arg 하드코딩 생성자 (`"TEST001"`, `"test@example.com"`, `"password"`) 제거 — `EmployeeServiceConcurrentTest`의 사용처를 7-arg로 갱신
- [x] **B3.** `changeTeam(Team)` 카운터 mutation 제거 — 참조 변경만 수행
- [x] **B4.** 컬럼 NOT NULL 어노테이션 보강 (`name`, `role`, `birthday`, `workStartDate`)
- [x] **B5.** 검증 로직(name/email/code/password) 9-arg 생성자에서 호출 — 정적 팩토리는 9-arg를 위임

### C. 도메인 (`Team`) — memberCount 제거
- [x] **C1.** `memberCount` 필드 제거
- [x] **C2.** `increaseMemberCount()`, `decreaseMemberCount()` 메서드 제거
- [x] **C3.** `getMemberCount()` 게터 제거
- [x] **C4.** 생성자 정리 — `Team(String)`, `Team(String, String)`, `Team(Long, String, String)`, `Team(Long, String, String, int annualLeaveCriteria)` 4종으로 정리. `register()` 정적 팩토리는 4-arg 생성자 위임

### D. 도메인 예외
- [x] **D1.** `EmployeeAlreadyExistsException` (`domain/employee/`) — `ofEmployeeCode` / `ofEmail` 정적 팩토리
- [x] **D2.** `EmployeeNotFoundException` (`domain/employee/`)
- [x] **D3.** `TeamNotFoundException` (`domain/team/`)
- [x] **D4.** `GlobalExceptionHandler`에 3개 핸들러 추가 (코드: `EMPLOYEE_ALREADY_EXISTS` 409, `EMPLOYEE_NOT_FOUND` 404, `TEAM_NOT_FOUND` 404)

### E. 리포지토리
- [x] **E1.** `EmployeeRepository.findEmployeeHierarchy()` → `findAllWithTeam()` 명명 변경
- [x] **E2.** `EmployeeRepository.countMembersByTeamIdsRaw(List<Long>)` 추가 + 서비스에서 `Map<Long, Long>` 변환 헬퍼

### F. 서비스 (`EmployeeService`)
- [x] **F1.** `registerEmployee` — 사전 중복 검사 + `Employee.register` + 인코딩 password + race 안전망(DataIntegrityViolationException 변환)
- [x] **F2.** `registerEmployee` — `teamId` 검증 (없으면 `TeamNotFoundException`) — `resolveTeam` 헬퍼
- [x] **F3.** `registerEmployee` — `EmployeeRegisterResponse` 반환
- [x] **F4.** `changeTeam(Long employeeId, Long teamId)` 신규 — employee/team 존재 검증, null teamId로 미배정 가능
- [x] **F5.** `findAllEmployee` — `findAllWithTeam` + DTO 매핑
- [x] **F6.** `updateEmployeeTeamName` 제거 (H의 PUT 재설계와 동시)

### G. 서비스 (`TeamService.findTeams`) — COUNT 파생
- [x] **G1.** `teamRepository.findAll()` → `EmployeeRepository.countMembersByTeamIdsRaw` → `TeamFindResponse.from(team, count)` 합성. 빈 팀 리스트 short-circuit
- [x] **G2.** `TeamFindResponse.from(Team, long memberCount)` 시그니처 변경 (memberCount 타입을 `long`으로 — COUNT 결과 타입 일치)

### H. 컨트롤러 (`EmployeeController`)
- [x] **H1.** `POST /employee` — 응답 `201 Created` + body `EmployeeRegisterResponse`
- [x] **H2.** `PUT /employee/{employeeId}/team` 신규 + 기존 `PUT /employee` 제거
- [x] **H3.** `GET /employee` — DTO 변경에 맞게 매핑 확인 (변경 불필요, DTO 자동 매핑)

### I. 스키마 / Flyway
- [x] **I1.** `src/main/resources/db/migration/V3__employee_constraints_and_drop_team_member_count.sql` 추가
  - `employee.name`, `employee.role`, `employee.birthday`, `employee.work_start_date` NOT NULL
  - `team.member_count` DROP COLUMN
- [x] **I2.** mysql 프로필 V3 적용 검증 (flyway_schema_history version 3 success=1, DESC employee/team로 컬럼 상태 직접 확인)
- [x] **I3.** `data.sql`(dev)에서 `member_count` 컬럼 참조 제거 — C 그룹에서 함께 처리됨

### J. 픽스처 정리
- [ ] **J1.** `Employees` 픽스처 — 4-arg 하드코딩 생성자 의존 제거 → `Employee.register(...)` 또는 명시적 9-arg 생성자 사용
- [ ] **J2.** `Teams` 픽스처 — `memberCount` 인자 의존 제거
- [ ] **J3.** 기존 1-① 테스트(TeamServiceTest/TeamControllerTest/TeamControllerDocsTest)에서 `memberCount` 검증부 갱신

### K. 테스트
- [ ] **K1.** `EmployeeServiceTest` — 등록 성공(team 포함/미포함), employeeCode 중복, email 중복, DataIntegrityViolation 변환, 존재하지 않는 teamId, password 해싱 검증
- [ ] **K2.** `EmployeeServiceTest` — changeTeam 성공(team↔null 양방향), employee 미존재, team 미존재
- [ ] **K3.** `EmployeeServiceTest` — findEmployees DTO 매핑
- [ ] **K4.** `TeamServiceTest` — 팀 조회 시 `memberCount`가 employee count와 일치 (0/1/N + 미배정 직원 존재 케이스)
- [ ] **K5.** `EmployeeControllerTest` — POST 권한 401/403/201, 필수 필드 누락 400, 중복 409, teamId 검증 404, 응답 `employeeId` 포함
- [ ] **K6.** `EmployeeControllerTest` — GET 응답 필드 + ISO-8601 형식 검증
- [ ] **K7.** `EmployeeControllerTest` — PUT /employee/{id}/team 권한, 검증, null teamId 허용
- [ ] **K8.** `TeamControllerTest` — 팀 조회 통합 시 `memberCount` COUNT 파생 정확성
- [ ] **K9.** `EmployeeControllerDocsTest` — 등록/조회/팀 변경 스니펫 갱신

### L. 검증
- [ ] **L1.** `./gradlew test --tests "com.company.officecommute.service.employee.*"` 통과
- [ ] **L2.** `./gradlew test --tests "com.company.officecommute.service.team.*"` 통과
- [ ] **L3.** `./gradlew test --tests "com.company.officecommute.controller.employee.*"` 통과
- [ ] **L4.** `./gradlew test --tests "com.company.officecommute.controller.team.*"` 통과
- [ ] **L5.** `./gradlew test --tests "com.company.officecommute.docs.*"` 통과
- [ ] **L6.** `./gradlew build` 전체 빌드 통과

### M. 마무리
- [ ] **M1.** `WORKS.md`의 1-② 항목 체크박스 `[x]`로 갱신
