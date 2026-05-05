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
