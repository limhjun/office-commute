# Tasks

PLAN.md를 실행 단위로 쪼갠 체크리스트. 작업이 끝난 항목은 `[x]`로 표시한다.

---

## 1-① 팀 등록 및 조회

### A. 요청/응답 DTO
- [x] **A1.** `TeamRegisterRequest`에 `String managerName` 필드 추가 (옵셔널, `@NotBlank` 미적용)
- [x] **A2.** `TeamFindResponse`에 `Long teamId` 필드 추가
- [x] **A3.** `TeamFindResponse.from(Team)` 매핑에 `team.getTeamId()` 반영

### B. 도메인 (`Team` 엔티티)
- [ ] **B1.** `name` 필드에 `@Column(nullable = false, unique = true)` 적용
- [ ] **B2.** `managerName`은 nullable 유지 (어노테이션 생략 또는 명시적 nullable=true)
- [ ] **B3.** 등록 흐름용 생성자 정리 — `new Team(String name, String managerName)` 형태로 사용 (memberCount=0, criteria=0 기본)
- [ ] **B4.** 사용처 없는 생성자 오버로딩 점검 (보수적으로 유지 가능, 명백한 데드 코드만 제거)

### C. 서비스 (`TeamService`)
- [ ] **C1.** `registerTeam`이 `managerName`을 함께 저장하도록 수정 (빈 문자열은 trim 후 null 정규화)
- [ ] **C2.** 사전 `findByName` 중복 검사 유지
- [ ] **C3.** `DataIntegrityViolationException` catch → `IllegalArgumentException("이미 존재하는 팀입니다.")`로 변환 (동시성 안전)
- [ ] **C4.** `findTeam`은 `teamRepository.findAll()` 그대로 사용 (JPQL `findTeam()` 호출 제거)

### D. 리포지토리 (`TeamRepository`)
- [ ] **D1.** `findTeam()` JPQL 메서드 삭제 (데드 코드)
- [ ] **D2.** `findByName` 시그니처 유지 확인

### E. 스키마 / Flyway
- [ ] **E1.** `src/main/resources/db/migration/V2__team_constraints.sql` 추가
  - `team.name` NOT NULL
  - `team.name` UNIQUE (`uk_team_name`)
  - `manager_name`은 변경 없음 (NULL 허용 유지)
- [ ] **E2.** mysql 프로필에서 마이그레이션 정상 적용 확인 (`SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun` 또는 통합 테스트)

### F. 컨트롤러 (`TeamController`)
- [ ] **F1.** 시그니처 변경 없음(요청/응답 DTO 변경에만 의존) — 빌드 통과 확인

### G. 테스트
- [ ] **G1.** `TeamServiceTest` — `managerName` 포함 등록 검증
- [ ] **G2.** `TeamServiceTest` — `managerName` null 등록 허용 검증
- [ ] **G3.** `TeamServiceTest` — 중복 이름 등록 시 `IllegalArgumentException`
- [ ] **G4.** `TeamServiceTest` — 동시성/`DataIntegrityViolationException` 변환은 단위 테스트로 구분 가능 시 추가
- [ ] **G5.** `TeamServiceTest` — 조회 결과에 `teamId` 매핑 검증
- [ ] **G6.** `TeamControllerTest` — 등록 요청 바디에 `managerName` 포함/누락 시 동작
- [ ] **G7.** `TeamControllerTest` — 조회 응답에 `teamId` 필드 존재
- [ ] **G8.** `TeamControllerDocsTest` — REST Docs 필드 스니펫 갱신 (요청/응답 양쪽)
- [ ] **G9.** 픽스처(`Teams`)에서 `Team` 생성자 호출부 일관성 확인 및 보정

### H. 검증
- [ ] **H1.** `./gradlew test --tests "com.company.officecommute.service.team.*"` 통과
- [ ] **H2.** `./gradlew test --tests "com.company.officecommute.controller.team.*"` 통과
- [ ] **H3.** `./gradlew test --tests "com.company.officecommute.docs.TeamControllerDocsTest"` 통과
- [ ] **H4.** `./gradlew build` 전체 빌드 통과
- [ ] **H5.** REST Docs 산출물 확인 (`build/docs/asciidoc/index.html`)

### I. 마무리
- [ ] **I1.** `WORKS.md`의 1-① 항목 체크박스 `[x]`로 갱신
- [ ] **I2.** 1-② 항목으로 이동 — 새 PLAN/tasks 섹션 추가
