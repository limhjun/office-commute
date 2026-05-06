# PLAN

WORKS.md 항목별 상세 개발 계획. 항목을 시작할 때 본 문서에 추가/갱신한다.

---

## 1-① 팀 등록 및 조회

### 목표
README에서 명시한 "**팀 이름, 관리자, 소속 인원**을 등록하고 전체 조회"를 실제 API/도메인/스키마에서 일관되게 충족시킨다.

### 현황 요약
- `POST /team` (Manager only) — `TeamRegisterRequest { teamName }` 만 받음
- `GET /team` — 전체 조회, 페이지네이션 없음
- 도메인 `Team { teamId, name, managerName, memberCount, annualLeaveCriteria }`
- 응답 DTO `TeamFindResponse { name, managerName, memberCount }` (teamId 없음)
- DB: `team` 테이블, `name` 컬럼에 UNIQUE/NOT NULL 제약 없음
- 중복 검사: `teamRepository.findByName(...)` 후 save (서비스 레벨)

### 식별된 문제점 / 갭
| # | 항목 | 현황 | 문제 |
|---|---|---|---|
| G1 | 등록 API 입력 | `teamName` 만 받음 | README가 명시한 "관리자"를 받지 못함. `managerName`이 항상 null로 저장됨 |
| G2 | 응답 DTO | `teamId` 미포함 | 후속 API(직원 등록 시 팀 지정 등)에서 식별자 사용 불가 |
| G3 | 중복 검사 동시성 | 서비스에서 `findByName` 후 `save` | 동시 요청 시 중복 팀 생성 가능. DB UNIQUE 제약 없음 |
| G4 | NOT NULL 제약 | `name`이 NULL 허용 | 도메인은 not blank 검증하지만 스키마가 보장하지 않음 |
| G5 | `findTeam()` JPQL | `new Team(name, managerName, memberCount)` — teamId 누락 | 사용처 없음 (controller는 `findAll()` 호출). 죽은 코드 |
| G6 | 페이지네이션 | 없음 | 팀 수가 많아지면 부담. 현 단계에선 정책 결정 필요 |
| G7 | `memberCount` 정합성 | Employee 등록/탈퇴 시 수동 증감 (`increase/decreaseMemberCount`) | 트랜잭션 누락/롤백 시 drift 위험. 차후 ②에서 함께 검토 |
| G8 | `annualLeaveCriteria` 등록 | 등록 API에서 받지 않음 | 항목 ⑥에서 다룰 영역 — 본 항목에선 default 0 유지 |

### 본 항목에서 처리할 범위 (제안)
- **포함**: G1, G2, G3, G4, G5
- **제외(추후 항목에서)**: G6 (페이지네이션 정책 결정 필요), G7 (②와 묶음), G8 (⑥)

### 변경 설계

#### (1) 요청 DTO 확장 — `TeamRegisterRequest`
```java
public record TeamRegisterRequest(
    @NotBlank String teamName,
    String managerName   // 옵셔널 — 매니저 미배정 팀 허용
) {}
```
- `managerName`은 **옵셔널** (매니저가 없는 팀도 허용한다는 도메인 규칙 반영). 빈 문자열이 들어오면 서비스에서 trim 후 빈 값이면 null로 정규화하는 처리 검토.
- `annualLeaveCriteria`는 본 항목 범위 외 — 제외.

#### (2) 도메인 생성자 정리 — `Team`
- 등록 흐름에서 사용할 생성자: `new Team(String name, String managerName)` (memberCount=0, criteria=0).
- 기존 다중 오버로딩 중 사용처 없는 것은 점검 후 정리(보수적으로 유지 가능).

#### (3) 응답 DTO — `TeamFindResponse`
```java
public record TeamFindResponse(
    Long teamId,
    String name,
    String managerName,
    int memberCount
) { ... }
```
- `teamId` 추가. `from(Team)`에서 `team.getTeamId()` 매핑.

#### (4) 서비스 — `TeamService.registerTeam`
- 사전 `findByName` 중복 검사 유지하되, **DB UNIQUE 위반(`DataIntegrityViolationException`)을 catch하여 `IllegalArgumentException("이미 존재하는 팀입니다.")`로 변환** — 동시성 경합 시 안전하게 충돌 처리.
- 대안: 사전 검사를 제거하고 UNIQUE 제약만으로 처리 (단순). → 메시지 일관성을 위해 catch 변환 방식 채택.

#### (5) 서비스 — `findTeam`
- `teamRepository.findAll()` 그대로 사용. `TeamRepository.findTeam()` JPQL은 사용처 없으면 제거(데드 코드 삭제).

#### (6) 스키마 변경 — Flyway 마이그레이션 추가
파일: `src/main/resources/db/migration/V2__team_constraints.sql`
```sql
ALTER TABLE team
    MODIFY name VARCHAR(255) NOT NULL,
    ADD CONSTRAINT uk_team_name UNIQUE (name);
```
- `manager_name`은 **NULL 허용** 유지 (매니저 미배정 팀 허용).
- H2(dev) 프로필은 Flyway 비활성 + `ddl-auto: create-drop`이라 영향 없음.
- mysql/prod는 새 마이그레이션으로 일관 보장.
- JPA 엔티티의 컬럼 어노테이션도 함께 추가하여 `ddl-auto: validate`와 정합 맞춤.

#### (7) 엔티티 매핑 보강 — `Team`
```java
@Column(nullable = false, unique = true)
private String name;

// managerName은 nullable 기본값 그대로 — 어노테이션 생략 가능
private String managerName;
```

### 테스트 계획
- `TeamServiceTest`
  - 등록 성공: `managerName`이 함께 저장되는지 검증.
  - 중복 이름 등록 시 `IllegalArgumentException`.
  - 조회 결과에 `teamId` 포함.
- `TeamControllerTest`
  - `POST /team` 요청 바디에 `managerName` 포함 케이스 / 누락 시 400.
  - `GET /team` 응답 JSON에 `teamId` 필드 존재.
- `TeamControllerDocsTest` (REST Docs) — 필드 스니펫 갱신.

### 검증 명령
```bash
./gradlew test --tests "com.company.officecommute.service.team.*"
./gradlew test --tests "com.company.officecommute.controller.team.*"
./gradlew test --tests "com.company.officecommute.docs.TeamControllerDocsTest"
```

### 영향 범위 / 주의사항
- `TeamRegisterRequest` 필드 추가는 클라이언트 호환성 깨짐(필수 필드 추가) — 현 단계 학습 프로젝트라 허용.
- 기존 테스트 픽스처(`Teams`)에서 `Team` 생성 호출 인자 일치 여부 확인 필요.
- `EmployeeService` 등 `team.increaseMemberCount()` 호출부는 본 항목에서 변경하지 않음 (G7로 이월).

### 결정사항 (사용자 확인 완료)
1. **`managerName`은 문자열 그대로 유지** — 매니저 직원 ID로 정규화하지 않는다.
2. **팀명 중복 검사는 대소문자 구분** — DB collation 그대로 사용. (별도 lower-case 정규화 없음)
3. **`TeamRepository.findTeam()` JPQL 삭제** — 데드 코드 제거 확정.
4. **매니저는 옵셔널** — 매니저가 없는 팀도 허용. DTO/스키마/도메인 모두 nullable.
5. **응답에서 매니저 null 표현은 JSON `null` 그대로** — 별도 치환 없음.
6. **매니저 배정은 등록 요청 바디에 옵셔널 포함** — 별도 PATCH 엔드포인트로 분리하지 않음 (단순 유지).
7. **팀 식별은 ID 기반** — 직원 등록 등 후속 API에서 팀을 `teamId`로 지정한다. 따라서 `TeamFindResponse`에 `teamId`를 노출한다. (이름 기반 컨벤션은 폐기 방향)

### 산출물 체크리스트
- [ ] `TeamRegisterRequest` 필드 추가 + 검증
- [ ] `Team` 엔티티 컬럼 제약 추가
- [ ] `TeamFindResponse`에 `teamId` 포함
- [ ] `TeamService.registerTeam` 동시성 처리
- [ ] `V2__team_constraints.sql` 추가
- [ ] 데드 코드 정리 (`TeamRepository.findTeam`)
- [ ] 단위/통합/REST Docs 테스트 갱신
- [ ] `WORKS.md` 1-① 체크박스 [x]

---

## 1-② 직원 등록 및 조회

### 목표
README의 "**직원의 이름, 소속 팀, 역할, 생년월일, 입사일을 등록하고 전체 조회**"를 production 품질로 일관되게 구현한다. 1-①에서 정립한 ID 기반 컨트랙트, 도메인 예외, Flyway, 정적 팩토리 패턴을 동일하게 적용한다.

### 현황 요약
- `POST /employee` (Manager only) — `EmployeeSaveRequest { name, role, birthday, workStartDate, employeeCode, email, password }` — **`teamId` 미포함**
- `GET /employee` — 전체 조회, 페이지네이션 없음
- `PUT /employee` — `EmployeeUpdateTeamNameRequest { employeeId, teamName }` — **이름 기반 팀 변경**
- 도메인 `Employee { employeeId, team, name, role, birthday, workStartDate, employeeCode, email, password }`
- `Employee.changeTeam(Team)` — 양쪽 팀의 `memberCount`를 직접 ±1 mutate
- 응답 DTO `EmployeeFindResponse { name, teamName, role, birthday, workStartDate }` — **`employeeId` 미포함, 날짜를 String(toString())으로 직렬화**
- `EmployeeService.registerEmployee` — 중복 시 `IllegalArgumentException`, **팀 미지정**, password를 `PasswordEncoder`로 해싱
- DB: `employee` 테이블, 컬럼 단위 NOT NULL 제약 부분만 적용 (`employeeCode`, `email`, `password`만 `@Column(nullable=false)`)

### 식별된 문제점 / 갭
| # | 항목 | 현황 | 문제 |
|---|---|---|---|
| G1 | 등록 시 팀 지정 | `EmployeeSaveRequest`에 `teamId` 없음 | 등록 직후 별도 PUT으로 팀 지정해야 함 — 2-step 등록 |
| G2 | PUT 엔드포인트 | 이름 기반 (`teamName`) | 1-①에서 ID 기반으로 결정 — 일관성 위반 |
| G3 | 등록 응답 | void/201 | 클라이언트가 후속 호출(상세/팀 변경)에 쓸 ID를 알 수 없음 |
| G4 | 조회 응답 | `employeeId` 미포함, 날짜 String | 후속 작업(연차/근태)에서 ID 필요. ISO-8601 직렬화 표준화 필요 |
| G5 | 중복 처리 | `IllegalArgumentException` | 1-①에서 정립한 도메인 예외 + 409 Conflict + 코드 컨벤션과 어긋남 |
| G6 | 동시성 안전망 | 사전 `existsBy*` 검사만 | DB UNIQUE 위반 시 변환 처리 없음 (1-①과 어긋남) |
| G7 | `Employee` 4-arg 테스트용 생성자 | 하드코딩 (`"TEST001"`, `"test@example.com"`, `"password"`) | production 코드에 테스트 픽스처 노출 — 도메인 오염 |
| **G8** | **`Team.memberCount` 정합성** | **denormalized counter, `Employee.changeTeam`이 mutate, 등록 시 누락 버그** | **drift 가능 + race(lost update) + 도메인 결합 + 현존 누락 버그** |
| G9 | NOT NULL 제약 | `name`, `role`, `birthday`, `workStartDate`, `team_id` 컬럼에 DB 제약 없음 | 도메인 검증과 스키마 보장 불일치 |
| G10 | 정적 팩토리 부재 | 다중 생성자만 존재 | 1-①에서 정립한 `Entity.register(...)` 패턴과 어긋남 |
| G11 | 페이지네이션 | 없음 | 1-①과 동일한 보류 사유 — 정책 결정 필요 (이번 항목 제외) |
| G12 | `findEmployeeHierarchy` 명명 | "hierarchy"라는 표현은 조직도 구조를 시사 | 실제로는 단순 `findAll` + `JOIN FETCH team` — 명명/책임 명확화 필요 |

### 본 항목에서 처리할 범위 (확정)
- **포함**: G1, G2, G3, G4, G5, G6, G7, **G8**, G9, G10, G12
- **제외(추후)**: G11 (페이지네이션은 별도 정책 항목)

### 핵심 설계 결정 — `Team.memberCount` 제거 (G8)

#### 배경
- `Team.memberCount`는 **denormalized counter** (직원 수의 요약을 별도 저장)
- 진실의 원천은 `employee.team_id`. `team.memberCount`는 그것의 중복 표현
- 중복된 진실 → 동기화 책임 → drift / race / 결합

#### 채택안: 옵션 A — 카운터 제거 + read 시 COUNT 파생
| | 현재 | 옵션 A |
|---|---|---|
| 진실의 원천 | `team.member_count` 컬럼 | `employee.team_id` 컬럼 (단일) |
| 직원 등록 시 | INSERT employee + UPDATE team SET member_count++ | INSERT employee. **끝** |
| 팀 이동 시 | 양쪽 team의 카운터 ±1 | `employee.team` 참조만 변경 |
| 팀 조회 시 | 컬럼 직접 read | `COUNT(*) GROUP BY team_id` 1회 |
| Drift 가능성 | 있음 | **구조적으로 불가능** |
| Race(lost update) | 있음 | **read-modify-write 패턴 자체가 사라짐** |

#### 비용/효과
- **비용**: 팀 조회 시 `COUNT(*) GROUP BY team_id` 1회 (FK 인덱스 활용, ~1ms, 결과 row 수 = 팀 수)
- **효과**: drift/race 원천 제거, 도메인 결합 해소, 향후 직원 삭제/퇴사 기능 추가 시 동기화 누락 위험 0

#### 적용 범위
- `Team`에서 `memberCount` 필드 + `increase/decreaseMemberCount()` 메서드 제거
- `Employee.changeTeam()`에서 카운터 mutation 코드 제거 (참조 변경만)
- `EmployeeRepository`에 `countMembersByTeamIds(List<Long>)` 추가
- `TeamFindResponse`는 `memberCount`를 응답 시점에 주입받는 형태로 변경
- `TeamService.findTeams()`가 직원 카운트 맵을 조회해 응답 합성
- Flyway V3로 `team.member_count` 컬럼 DROP

### 변경 설계

#### (1) 요청 DTO — `EmployeeSaveRequest`
```java
public record EmployeeSaveRequest(
    @NotBlank String name,
    @NotNull Role role,
    @NotNull @Past LocalDate birthday,
    @NotNull @PastOrPresent LocalDate workStartDate,
    @NotBlank @Pattern(regexp = "^[A-Z0-9]{6,10}$") String employeeCode,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password,
    Long teamId   // 옵셔널 — 미배정 직원 허용
) {}
```
- `teamId`는 **옵셔널** (HR 관행상 입사 직후 팀 미배정 상태 존재 가능)
- 옵셔널이지만 값이 들어오면 존재 검증 → 없으면 `TeamNotFoundException` 404

#### (2) 등록 응답 DTO 신규 — `EmployeeRegisterResponse`
```java
public record EmployeeRegisterResponse(Long employeeId) {}
```
- 컨트롤러는 `201 Created` + body로 `{ employeeId }` 반환
- 클라이언트가 후속 호출(상세 조회, 팀 변경 등)에 즉시 활용

#### (3) 조회 응답 DTO — `EmployeeFindResponse`
```java
public record EmployeeFindResponse(
    Long employeeId,
    Long teamId,           // null 가능 (미배정)
    String teamName,       // null 가능 (미배정)
    String name,
    String role,
    LocalDate birthday,    // ISO-8601 직렬화 (Jackson 기본)
    LocalDate workStartDate
) { ... }
```
- `employeeId`, `teamId` 추가
- `birthday`/`workStartDate`는 `LocalDate` 그대로 — Jackson이 ISO-8601(`yyyy-MM-dd`)로 직렬화 (현재 String 변환 로직 제거)
- `email`, `password`, `employeeCode`는 응답에 노출 안 함 (PII/보안)

#### (4) PUT 엔드포인트 재설계 — ID 기반
- 기존: `PUT /employee` + `EmployeeUpdateTeamNameRequest { employeeId, teamName }`
- 신규: `PUT /employee/{employeeId}/team` + `EmployeeChangeTeamRequest { Long teamId }` (null 허용 → 팀 미배정으로 변경 가능)
- `EmployeeUpdateTeamNameRequest` 삭제

#### (5) 도메인 — `Employee` 정리
- 정적 팩토리 `Employee.register(...)` 추가 (DTO 필드 + 인코딩된 password + Team)
- 4-arg 하드코딩 생성자 제거 → 테스트 픽스처(`Employees`)에서 명시적 값 사용으로 전환
- `changeTeam(Team newTeam)` 단순화: `this.team = newTeam` (카운터 mutation 제거)
- 검증 로직 (validateName/Email/Code/Password)은 정적 팩토리 + 9-arg 생성자에서 일관 호출

#### (6) 도메인 — `Team` 정리
- `memberCount` 필드 제거
- `increaseMemberCount()`, `decreaseMemberCount()` 메서드 제거
- `getMemberCount()` 게터 제거 (응답은 별도 경로로 주입)

#### (7) 도메인 예외
- `EmployeeAlreadyExistsException(String employeeCode 또는 email)` — 409
- `EmployeeNotFoundException(Long employeeId)` — 404
- `TeamNotFoundException(Long teamId)` — 404 (팀 지정/변경 시 검증)
- `GlobalExceptionHandler`에 각 핸들러 추가 (코드: `EMPLOYEE_ALREADY_EXISTS`, `EMPLOYEE_NOT_FOUND`, `TEAM_NOT_FOUND`)

#### (8) 서비스 — `EmployeeService`
- `registerEmployee(EmployeeSaveRequest)`:
  - 사전 `existsByEmployeeCode` / `existsByEmail` → `EmployeeAlreadyExistsException`
  - `teamId` 있으면 `teamRepository.findById` → 없으면 `TeamNotFoundException`
  - `Employee.register(...)` + 인코딩된 password로 저장
  - `try { save } catch (DataIntegrityViolationException) { throw EmployeeAlreadyExistsException }` (race 안전망)
  - 반환: `EmployeeRegisterResponse(savedId)`
- `changeTeam(Long employeeId, Long teamId)`:
  - employee 존재 확인 → 없으면 `EmployeeNotFoundException`
  - teamId 있으면 team 존재 확인 → 없으면 `TeamNotFoundException`
  - `employee.changeTeam(team)` 호출 (카운터 mutation 없음)
- `findEmployees()`:
  - `employeeRepository.findAllWithTeam()` (현 `findEmployeeHierarchy`를 명명 변경)
  - DTO 매핑

#### (9) 서비스 — `TeamService.findTeams` 변경
- 기존: `team.memberCount`를 그대로 응답
- 신규:
  ```java
  List<Team> teams = teamRepository.findAll();
  List<Long> teamIds = teams.stream().map(Team::getTeamId).toList();
  Map<Long, Long> counts = employeeRepository.countMembersByTeamIds(teamIds);
  return teams.stream()
      .map(t -> TeamFindResponse.from(t, counts.getOrDefault(t.getTeamId(), 0L)))
      .toList();
  ```
- `TeamFindResponse.from(Team, long memberCount)` 시그니처로 변경

#### (10) 리포지토리 — `EmployeeRepository`
- `findEmployeeHierarchy()` → `findAllWithTeam()` 명명 변경 (의미 일치)
- 추가:
  ```java
  @Query("""
      SELECT e.team.teamId, COUNT(e)
      FROM Employee e
      WHERE e.team.teamId IN :teamIds
      GROUP BY e.team.teamId
  """)
  List<Object[]> countMembersByTeamIdsRaw(@Param("teamIds") List<Long> teamIds);
  ```
  - 서비스 레이어에서 `Map<Long, Long>`으로 변환하는 헬퍼 또는 default method 제공

#### (11) 스키마 변경 — Flyway V3
파일: `src/main/resources/db/migration/V3__employee_constraints_and_member_count.sql`
```sql
-- Employee 컬럼 NOT NULL 보강
ALTER TABLE employee
    MODIFY COLUMN name VARCHAR(255) NOT NULL,
    MODIFY COLUMN role VARCHAR(50) NOT NULL,
    MODIFY COLUMN birthday DATE NOT NULL,
    MODIFY COLUMN work_start_date DATE NOT NULL;

-- Team.member_count 컬럼 제거
ALTER TABLE team DROP COLUMN member_count;
```
- `team_id`는 NULL 허용 유지 (미배정 직원 허용)
- 운영 적용 전: `name`/`role`/`birthday`/`work_start_date`에 NULL row 없는지 점검 필요
- mysql/prod fresh 적용 검증 필수

#### (12) 엔티티 매핑 보강 — `Employee`
```java
@Column(nullable = false) private String name;
@Enumerated(EnumType.STRING) @Column(nullable = false) private Role role;
@Column(nullable = false) private LocalDate birthday;
@Column(nullable = false) private LocalDate workStartDate;
// team_id (FK)는 nullable=true 유지
```

### 테스트 계획

#### 단위 — `EmployeeServiceTest`
- 등록 성공 (teamId 포함 / 미포함 두 경우)
- 등록 시 employeeCode 중복 → `EmployeeAlreadyExistsException`
- 등록 시 email 중복 → `EmployeeAlreadyExistsException`
- 등록 시 `DataIntegrityViolationException` → `EmployeeAlreadyExistsException` 변환 (race 안전망)
- 등록 시 존재하지 않는 teamId → `TeamNotFoundException`
- password 해싱 검증 (저장된 값이 평문이 아님)
- changeTeam 성공 (team → null, null → team, team → team 세 케이스)
- changeTeam 시 employee 없음 → `EmployeeNotFoundException`
- changeTeam 시 teamId 없음 → `TeamNotFoundException`
- findEmployees 성공 — DTO 필드 매핑 검증

#### 단위 — `TeamServiceTest` 갱신 (memberCount 파생화 영향)
- 팀 조회 시 `memberCount`가 employee count 결과와 일치 (직원 0/1/N명 케이스)
- 빈 팀 / 미배정 직원 존재 시에도 정확히 카운트

#### 컨트롤러 — `EmployeeControllerTest`
- POST /employee — 권한 401/403/201, 필수 필드 누락 400, 옵셔널 teamId 포함/제외, 중복 409, 존재하지 않는 teamId 404, 응답 body에 `employeeId` 포함
- GET /employee — 응답에 `employeeId`/`teamId`(null 허용)/`teamName`(null 허용)/날짜 ISO-8601 형식 검증
- PUT /employee/{id}/team — 권한, employee/team 존재 검증, teamId null로 미배정 가능

#### 컨트롤러 — `TeamControllerTest` 갱신
- 팀 조회 응답의 `memberCount`가 employee 데이터로부터 정확히 계산되는지 통합 검증

#### REST Docs — `EmployeeControllerDocsTest`
- 등록 요청/응답 스니펫 (teamId 옵셔널, employeeId 응답)
- 조회 응답 스니펫 (새 필드 + ISO-8601)
- PUT 팀 변경 스니펫

### 검증 명령
```bash
./gradlew test --tests "com.company.officecommute.service.employee.*"
./gradlew test --tests "com.company.officecommute.service.team.*"
./gradlew test --tests "com.company.officecommute.controller.employee.*"
./gradlew test --tests "com.company.officecommute.controller.team.*"
./gradlew test --tests "com.company.officecommute.docs.*"
./gradlew build
```

### 영향 범위 / 주의사항
- **`Team.memberCount` 제거는 광범위 영향**: `Teams` 픽스처, `Team` 생성자 호출부, `TeamServiceTest`, `TeamControllerTest`, `TeamControllerDocsTest`, 기존 1-① 테스트 일부 갱신 필요
- `EmployeeUpdateTeamNameRequest` 삭제는 클라이언트 호환성 깨짐 — production 단계 아니라 허용
- `data.sql` (dev)에 `member_count` 참조 있으면 함께 정리
- `Employee` 4-arg 생성자 제거 시 테스트 픽스처 전반 영향 — `Employees` 픽스처 정리 동반

### 결정사항 (사용자 확인 완료)
1. **`teamId`는 `EmployeeSaveRequest`에 옵셔널로 포함** — 미배정 직원 허용.
2. **PUT 엔드포인트 ID 기반 재설계** — `PUT /employee/{id}/team` + `{ teamId }`. 이름 기반 폐기.
3. **등록 응답에 `employeeId` 포함** — `EmployeeRegisterResponse` 신규.
4. **`Team.memberCount` 필드 제거 + read 시 COUNT 파생 (옵션 A)** — Flyway V3로 컬럼 DROP.

### 산출물 체크리스트
- [ ] `EmployeeSaveRequest`에 `teamId` 옵셔널 추가
- [ ] `EmployeeRegisterResponse` 신규
- [ ] `EmployeeFindResponse` 필드 확장 + 날짜 타입 정규화
- [ ] `EmployeeChangeTeamRequest` 신규 + `EmployeeUpdateTeamNameRequest` 삭제
- [ ] `Employee.register` 정적 팩토리 + 4-arg 하드코딩 생성자 제거
- [ ] `Employee.changeTeam` 카운터 mutation 제거
- [ ] `Team.memberCount` 필드/메서드 제거
- [ ] `EmployeeAlreadyExistsException`, `EmployeeNotFoundException`, `TeamNotFoundException` + `GlobalExceptionHandler` 핸들러
- [ ] `EmployeeService` 재작성 (register/changeTeam/findEmployees)
- [ ] `TeamService.findTeams` — COUNT 파생으로 변경
- [ ] `EmployeeRepository.countMembersByTeamIdsRaw` 추가, `findAllWithTeam` 명명
- [ ] `TeamController` PUT 경로 재설계
- [ ] `V3__employee_constraints_and_member_count.sql` 추가
- [ ] mysql 프로필 마이그레이션 검증
- [ ] 단위/통합/REST Docs 테스트 갱신
- [ ] `WORKS.md` 1-② 체크박스 [x]
