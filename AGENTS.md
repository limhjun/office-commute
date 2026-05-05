## Project Snapshot
- Java 21, Spring Boot 3.5.5, Gradle wrapper.
- Databases: H2 for `dev`, MySQL 8.0 for local `mysql` and `prod`.
- Tests: JUnit 5, Spring Boot test, MockMvc/MockMvcTester, Mockito, Spring REST Docs.

## Configuration
- Default profile is `dev` from `src/main/resources/application.yml`.
- `dev` uses H2 TCP at `jdbc:h2:tcp://localhost/~/test`, `ddl-auto: create-drop`, `data.sql`, and Flyway disabled.
- `mysql` uses local MySQL, Flyway, and `ddl-auto: validate`; `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` can override defaults.
- `prod` uses MySQL 8.0, Flyway, and `ddl-auto: validate`.
- Flyway migrations live in `src/main/resources/db/migration/V*__*.sql`; schema changes for MySQL/prod should be new migration files.
- Environment variables can be loaded from `.env`; start from `.env.example`.
- Public holiday API calls require `PUBLIC_API_SERVICE_KEY`.

## Output Format
- 도구 사용 시 앞에 🥕 이모지를 붙여서 표시

## Language
For every prompt I enter, whether in Korean or English, first rewrite it into proper English. If I write in English, point out any grammatical errors and suggest improvements to make the sentence more natural. Then, proceed with the rewritten, polished English prompt.
