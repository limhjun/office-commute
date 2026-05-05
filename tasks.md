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
- [ ] **I2.** 1-② 항목 시작 시 PLAN/tasks 섹션 추가
