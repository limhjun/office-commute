# CLAUDE.md

## Build / Test
- Run app (dev, H2 TCP): `./gradlew bootRun --args='--spring.profiles.active=dev'`
- Run app (mysql): `./gradlew bootRun --args='--spring.profiles.active=mysql'`
- Tests: `./gradlew test`
- Full check (tests + `openApiValidate`): `./gradlew check`
- Build jar: `./gradlew build`

## Project Structure
- `src/main/java/com/company/officecommute/` — layered: `controller / service / domain / repository / dto / web / auth / global / config`
- `src/main/resources/db/migration/V*__*.sql` — Flyway (MySQL/prod schema, canonical)
- `src/main/resources/data.sql` — H2 dev seed
- `src/main/resources/application*.yml` — profile configs (`dev`, `mysql`, `prod`)
- `src/test/java/...` — mirrors main package layout
- `openapi.yml` (root) — wire-format source of truth
- `README.md` — project overview and local setup
- `scripts/api_test.sh` — manual API smoke test

## Profiles
- `dev`: H2 TCP `jdbc:h2:tcp://localhost/~/test`, `ddl-auto: create-drop`, `data.sql` on, Flyway off.
- `mysql` / `prod`: Flyway on, `ddl-auto: validate`. Overrides via `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`.
- Holiday API requires `PUBLIC_API_SERVICE_KEY`.
- **Do not commit secret values or per-developer settings.** Runtime env values go in `.env` (gitignored), based on `.env.example`; agent-only local notes may go in `CLAUDE.local.md` (gitignored). Only the *names* of required env vars belong in tracked docs.

## Coding Conventions
- Java 21, Spring Boot 3.5. 4-space indentation, no tabs.
- Date/time: avoid `LocalDateTime` (timezone-ambiguous). Use `ZonedDateTime` for timestamps (already standard in commute/overtime), `LocalDate` / `YearMonth` for calendar dates. Inject `Clock` for "now" so tests can control time; when domain logic needs "today", pass a `LocalDate` derived from that `Clock` rather than calling `LocalDate.now()` directly. Never downgrade dates to `String`.
- Naming: controllers `*Controller`, services `*Service`, repos `*Repository`, DTOs `*Request` / `*Response`, exceptions `*Exception`.
- Domain exceptions: one class per business meaning (e.g. `TeamAlreadyExistsException`). Never reuse `IllegalArgumentException` for business errors.
- Error envelope: `ErrorResult { code, message }` or `ValidationErrorResult { code, message, fieldErrorResults }`. Codes follow `*_ALREADY_EXISTS`, `*_NOT_FOUND`, `VALIDATION_ERROR`, `INVALID_JSON`.
- Static factories for registration: `Entity.register(...)`; validation/normalization lives there or in the canonical full-arg constructor.
- Three-layer validation: domain constructor/factory + JPA `@Column(nullable=false, unique=…)` + Flyway DDL must agree.
- Boundary-validation contract: every user-input format rule the domain enforces (standard exceptions like `IllegalArgumentException`) must also be covered by Bean Validation on the request DTO (custom constraint if needed, e.g. `@ValidZoneId`). Domain standard exceptions map to 500 `UNEXPECTED_DOMAIN_VIOLATION` on purpose — reaching one from user input means a missing DTO check, not a user error.
- ID-based API: registration returns `{ entityId }`; mutation endpoints carry the ID in the path (`PUT /employee/{employeeId}/team`). Don't introduce name-based contracts.

## Always
- **Spec-first**: update `openapi.yml` whenever a controller or DTO changes. No `@Operation` / `@ApiResponse` annotations on controllers — the yml is the spec.
- **Never edit an applied Flyway `V*__*.sql` file** (checksum). Schema changes go in a new `V` file.
- When a column changes, update `data.sql` too — otherwise `dev` boot fails.
- Throw domain exceptions, don't catch — `GlobalExceptionHandler` (`@RestControllerAdvice`) converts them. After a uniqueness pre-check, catch `DataIntegrityViolationException` and re-throw the matching domain conflict exception (e.g. `*_ALREADY_EXISTS`, `DUPLICATE_WORK`, `ANNUAL_LEAVE_DUPLICATE`) as a race safety net.
- Default: derive aggregates instead of storing them (e.g. `Team.memberCount` → `COUNT(*) GROUP BY team_id`) — removes sync-bug surface. Escape hatch: if a query is measurably slow under realistic load (not hypothetically), introduce a stored counter / cache with the invariant documented and the update path covered by tests. Don't add one preemptively.

> Scoped rules: test conventions live in `.claude/rules/testing.md`, auth rules for controllers in `.claude/rules/auth.md` (auto-loaded by path).
