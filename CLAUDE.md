# CLAUDE.md

## Language Rules (IMPORTANT)
For every prompt I enter, whether in Korean or English, first rewrite it into proper English.
If I write in English, point out any grammatical errors and suggest improvements to make the sentence more natural.
Then, proceed with the rewritten, polished English prompt.

**YOU MUST ALWAYS RESPOND IN ENGLISH.**
This applies to ALL outputs: explanations, code comments, commit messages, PR descriptions, and clarifying questions.
NEVER respond in Korean, even if I write in Korean.
Skip the rewrite step if my prompt is already short, clear, and grammatically correct.

## Stack
- Java 21, Spring Boot 3.5.5, Gradle wrapper.
- DB: H2 (`dev`), MySQL 8.0 (`mysql`, `prod`).
- Tests: JUnit 5, Spring Boot test, MockMvcTester, Mockito, `@MockitoBean`.
- API spec: `openapi.yml` (OpenAPI 3.0.3); validated by `./gradlew openApiValidate` (wired to `check`).

## Profiles
- `dev`: H2 TCP `jdbc:h2:tcp://localhost/~/test`, `ddl-auto: create-drop`, `data.sql`, Flyway off.
- `mysql` / `prod`: Flyway on, `ddl-auto: validate`. `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` overrides.
- Flyway scripts at `src/main/resources/db/migration/V*__*.sql`. **Never edit an applied V file** (checksum). Schema changes always go in a new V file.
- Holiday API needs `PUBLIC_API_SERVICE_KEY`.

## Workflow (per README "주요 기능" item)
- `WORKS.md` lists items; tick `[x]` on completion.
- Per item: read current state → diagnostic with numbered gaps (G1..Gn) → user approval → write `PLAN.md` + `tasks.md` → implement in groups (A, B…) → commit per green group → final commit ticking `WORKS.md`.
- Decisions go into `PLAN.md` as user-confirmed entries. No broad changes without approval.

## Sources of Truth (drift kills these)
- **`openapi.yml`** — wire format for every controller/DTO. Update it whenever a controller or DTO changes. `openApiValidate` checks the yml only, not code-spec alignment. Spec-first: **no `@Operation` / `@ApiResponse` annotations on controllers**.
- **Flyway V files** — MySQL/prod schema. Keep JPA `@Column`, Flyway DDL, and domain validation in three-way agreement.
- **`data.sql`** — H2 dev seed. Update on column changes or `dev` boot fails.
- **`MEMORY.md`** (`~/.claude/projects/.../memory/`) — user prefs and project decisions. Update when decisions change.

## Coding Conventions
- **Static factories**: registration flows use `Entity.register(...)`. Validation/normalization (blank → null, etc.) lives there or in the canonical 9-arg constructor.
- **Domain exceptions**: one class per business meaning (`TeamAlreadyExistsException`, `EmployeeNotFoundException`, …). Never reuse `IllegalArgumentException` for business errors. Throw, don't catch — `@RestControllerAdvice` converts.
- **Single error envelope**: every domain exception gets a `@ExceptionHandler` in `GlobalExceptionHandler`. Responses are `ErrorResult { code, message }` or `ValidationErrorResult { code, message, fieldErrorResults }`.
- **HTTP status**: duplicate = 409 (`*_ALREADY_EXISTS`); not found = 404 (`*_NOT_FOUND`); forbidden = 403; unauthenticated = 401; validation = 400 (`VALIDATION_ERROR` / `INVALID_JSON`).
- **Race safety net**: catch `DataIntegrityViolationException` after the pre-check and re-throw as the matching `*_ALREADY_EXISTS`. The point is meaning conversion, not silencing.
- **ID-based API**: registration responses carry the new ID (`{ employeeId }`). Mutation endpoints put the ID in the path (`PUT /employee/{employeeId}/team`). Name-based contracts are deprecated.
- **No stored counters/summaries** when derivable. `Team.memberCount` → `COUNT(*) GROUP BY team_id`. Removing the sync burden beats locking it correctly.
- **DTO types match meaning**: calendar dates = `LocalDate` (Jackson → ISO-8601). Absolute moments = `Instant` or `OffsetDateTime`. Avoid `LocalDateTime` (timezone-ambiguous). Never downgrade dates to `String`.
- **Three-layer validation**: domain constructor/factory + JPA `@Column(nullable=false, unique=…)` + Flyway DDL must agree.

## Auth
- Session-based (`JSESSIONID`). Login invalidates any existing session, then stores `currentEmployeeId` and `currentRole` on the new one (session-fixation defense).
- `@ManagerOnly` admits only `currentRole == MANAGER`.
- Current user in controllers: `@RequestAttribute("currentEmployeeId") Long employeeId`.

## Output Format
- Prefix tool use with 🥕.
