# CLAUDE.md

## Stack
- DB: H2 (`dev`), MySQL 8.0 (`mysql`, `prod`).
- Tests: `MockMvcTester` (not legacy MockMvc), `@MockitoBean` (not the old `@MockBean`).

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
- **Flyway V files** — canonical MySQL/prod schema.
- **`data.sql`** — H2 dev seed. Update on column changes or `dev` boot fails.

## Coding Conventions
- **Static factories**: registration flows use `Entity.register(...)`. Validation/normalization (blank → null, etc.) lives there or in the canonical 9-arg constructor.
- **Domain exceptions**: one class per business meaning (`TeamAlreadyExistsException`, `EmployeeNotFoundException`, …). Never reuse `IllegalArgumentException` for business errors. Throw, don't catch — `@RestControllerAdvice` converts.
- **Single error envelope**: every domain exception gets a `@ExceptionHandler` in `GlobalExceptionHandler`. Responses are `ErrorResult { code, message }` or `ValidationErrorResult { code, message, fieldErrorResults }`.
- **Error codes**: `*_ALREADY_EXISTS`, `*_NOT_FOUND`, `VALIDATION_ERROR`, `INVALID_JSON`. Follows standard HTTP semantics.
- **Race safety net**: catch `DataIntegrityViolationException` after the pre-check and re-throw as the matching `*_ALREADY_EXISTS`. The point is meaning conversion, not silencing.
- **ID-based API**: registration responses carry the new ID (`{ employeeId }`). Mutation endpoints put the ID in the path (`PUT /employee/{employeeId}/team`). Name-based contracts are deprecated.
- **No stored counters/summaries** when derivable. `Team.memberCount` → `COUNT(*) GROUP BY team_id`. Removing the sync burden beats locking it correctly.
- **Date/time types**: avoid `LocalDateTime` (timezone-ambiguous). Never downgrade dates to `String`.
- **Three-layer validation**: domain constructor/factory + JPA `@Column(nullable=false, unique=…)` + Flyway DDL must agree.

## Auth
- Session-based (`JSESSIONID`). Login invalidates any existing session, then stores `currentEmployeeId` and `currentRole` on the new one (session-fixation defense).
- `@ManagerOnly` admits only `currentRole == MANAGER`.
- Current user in controllers: `@RequestAttribute("currentEmployeeId") Long employeeId`.
