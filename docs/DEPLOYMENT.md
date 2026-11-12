# 배포 전략 (Deployment Strategy)

> 작성일: 2026-07-09 · 갱신일: 2026-08-14 · 대상: office-commute (Spring Boot 3.5 / Java 21 백엔드 + React 18 SPA 관리자 프론트)
> 전제: 사내 소규모 관리자용 서비스, 세션 쿠키(JSESSIONID) 기반 인증, 단일 리전 — EC2 단일 서버.
> 코드 주석들이 이 문서의 §번호를 참조하므로 섹션 번호는 바꾸지 않는다.

## 0. 결론 요약

- 인증 방식이 **서버 세션 + 쿠키**이고 백엔드에 CORS 설정이 없으므로, 프론트와 백엔드는 반드시 **같은 오리진**으로 배포한다. (이 전제는 코드 전반 — vite 프록시, `client.ts`의 상대 baseUrl — 에 이미 깔려 있다.)
- 권장 아키텍처: **단일 서버 + Docker Compose** — Nginx(TLS 종료 + SPA 정적 서빙 + `/api` 리버스 프록시) / Spring Boot 컨테이너 / MySQL(가능하면 관리형 DB). 구현은 `deploy/` 디렉토리에 있다 (§2).
- 최초 작성(07-09) 시점의 **배포 차단 이슈 4건(Phase 0)은 전부 코드에 반영되어 해소되었다** (§1). 남은 것은 서버 준비·TLS 발급·시크릿 주입 등 운영 절차다 (§2.1, §5).

## 1. Phase 0 — 배포 차단 이슈 (전부 해소됨)

2026-07-09 검증에서 발견된 4건. 이후 모두 반영되었으므로 무엇이었고 어떻게 닫혔는지만 기록한다.

| # | 이슈 (당시) | 해소 근거 (현재) |
|---|---|---|
| 1-1 | API가 루트에 흩어져(`/team`, `/overtime` 등) SPA 라우트와 정확히 충돌 — 새로고침/딥링크 시 XML 에러 (실증됨) | 전 컨트롤러 `@RequestMapping("/api")`로 이동 + `openapi.yml`·`schema.d.ts` 재생성 + vite 프록시 `/api` 단일화. 프록시 규칙은 영원히 "`/api/**` → 백엔드, 나머지 → SPA" 한 줄 |
| 1-2 | `AuthInterceptor`가 `/**`에 걸려 비로그인 시 정적 자산까지 401 — 로그인 페이지 자체가 로드 불가 (실증됨) | 인터셉터를 `/api/**`에만 적용(`/api/auth/**` 제외), 죽은 예외 `/h2-console/**` 제거 — `WebConfig.java` |
| 1-3 | `frontend/dist` 서빙 주체 부재 (jar 포함 태스크·`static/`·SPA fallback 없음) | Nginx가 dist 서빙 + SPA fallback — `deploy/nginx.conf` |
| 1-4 | 시드 admin 비밀번호(`admin1234`)가 저장소에 평문 노출, 변경 수단 없음 | `V11__rotate_admin_password.sql` + Flyway placeholder. prod는 `ADMIN_PASSWORD_HASH` 미설정 시 부팅 실패가 정상. 적용 후 placeholder 값만 바꿔도 재실행되지 않으므로, 이후 로테이션은 비밀번호 변경 API(§6 중기 과제)로 |

## 2. 목표 아키텍처

```
[브라우저]
   │ HTTPS (443)
   ▼
[Nginx]  ── TLS 종료 (Let's Encrypt/certbot)
   ├── /api/**  → proxy_pass http://app:8080  (쿠키/헤더 전달)
   └── 그 외    → frontend dist 정적 서빙, 미매칭 경로는 index.html (SPA fallback)
        │
   [app: Spring Boot jar, prod 프로파일]  ── 단일 인스턴스
        │
   [MySQL 8]  ── 관리형 DB(RDS 등) 권장, 불가 시 Compose 컨테이너 + 볼륨
```

**구현 파일이 canonical이다.** 이 문서의 스니펫이 아니라 아래 파일을 본다:

- `deploy/nginx.conf` — 운영 라우팅 실물(서버 사본과 동일 유지). 현재 `office-commute.com`(주) + `13-125-8-177.sslip.io`(레거시) 2도메인 서빙, 인증서는 도메인별 발급
- `deploy/docker-compose.prod.yml` — 운영 compose. 사용법·주입 변수는 파일 상단 주석
- `deploy/nginx.local.conf` + 루트 `docker-compose.yml --profile full` — 배포 전 로컬 풀스택 리허설. 라우팅은 운영과 동일, TLS만 없음

백엔드 prod 프로파일에는 HTTPS 프록시 뒤 세션 쿠키 보안 설정이 **이미 적용되어 있다** (`application-prod.yml`): `server.forward-headers-strategy: framework`(X-Forwarded-* 신뢰) + 세션 쿠키 `secure/http-only/same-site: lax`.

**선택 근거**
- *단일 서버 + Compose인 이유*: 관리자용 소규모 트래픽, 세션이 톰캣 인메모리라 어차피 수평 확장 불가(스케일아웃하려면 Spring Session + Redis 선행 필요 — §6). 지금 쿠버네티스/멀티 인스턴스는 과잉.
- *Nginx가 dist를 서빙하는 이유*: Spring static 방식은 1-3의 추가 구현이 필요하고, 프론트만 고치는 배포에도 백엔드 재시작(=전 직원 세션 로그아웃)이 강제된다. 분리하면 프론트 배포는 파일 교체 + reload로 무중단.
- *관리형 DB 권장 이유*: 백업/패치 자동화. 불가 시 Compose MySQL + `mysqldump` 일일 크론 백업을 최소선으로.

### 2.1 EC2 준비 + TLS 부트스트랩 (최초 1회)

1. **보안 그룹**: 인바운드는 80/443(+운영자 IP 한정 22)만 연다. **8080·3306은 절대 공개하지 않는다** — nginx→app은 compose 내부 네트워크로 통신하고, RDS는 별도 SG로 app 서버에서만 허용한다.
2. **EIP 고정 + DNS A 레코드를 먼저** 만든다. 인증서 발급이 도메인 검증(HTTP-01)이므로 DNS가 인증서보다 선행이다.
3. Docker + compose 플러그인 설치. 소형 인스턴스(t3.small 이하)라면 스왑 2GB를 잡는다 — JVM 기본 힙(RAM의 25%) + nginx만으로도 여유가 없다.
4. **TLS 최초 발급 — 닭과 달걀 주의**: `deploy/nginx.conf`는 인증서 파일이 있어야 기동한다. 따라서 **compose를 띄우기 전에** 80 포트가 빈 상태에서 발급한다:
   `sudo certbot certonly --standalone -d <도메인>`
5. **갱신은 webroot 방식으로**: standalone 갱신은 80 포트를 요구해 기동 중인 nginx와 충돌한다. dist 디렉토리를 webroot로 쓰면 `try_files $uri`가 `/.well-known/acme-challenge/*` 파일을 그대로 서빙하므로 nginx 설정 추가가 필요 없다. 단, 80 서버는 301 리다이렉트뿐이므로 챌린지는 HTTPS로 따라와야 한다 — certbot은 리다이렉트를 따라가므로 동작한다:
   `certbot renew --webroot -w <compose 옆 dist 절대경로>` + 갱신 훅(`--deploy-hook`)에서 `docker compose exec nginx nginx -s reload`.

## 3. 빌드·배포 파이프라인 (GitHub Actions)

1. **CI (PR/main push)** — `.github/workflows/ci.yml`로 구현됨. `backend` 잡이 `./gradlew check`(테스트 + openApiValidate), `frontend` 잡이 `pnpm install --frozen-lockfile && pnpm lint && pnpm build`(tsc 포함)를 돈다. Testcontainers 사용하므로 러너에 Docker 필요 — `ubuntu-latest`는 기본 제공 (Testcontainers 1.21.4 핀 유지). 잡 이름 `backend`/`frontend`를 `main` 브랜치 보호의 required status check로 등록해야 실제로 머지를 막는다.
2. **CD (CI 성공 후 자동/수동 트리거)** — `.github/workflows/deploy.yml`로 구현됨. `main`의 CI가 성공하면 자동 실행하고, `main`에서 `workflow_dispatch`로 재실행할 수도 있다.
   - GitHub는 OIDC로 단기 AWS 자격증명을 발급받는다. 장기 Access Key와 운영 애플리케이션 시크릿은 GitHub에 저장하지 않는다.
   - 백엔드: `./gradlew bootJar` → Docker 이미지 빌드 → 커밋 SHA 태그로 private ECR push. **`build`가 아니라 `bootJar`를 돌린다** — `build`는 plain jar까지 만들어 `Dockerfile`의 `COPY build/libs/*.jar`가 두 파일에 매칭돼 깨진다 (`jar { enabled = false }`로 못박는 것은 §6 로드맵).
   - 프론트: `pnpm build` → compose/nginx 설정과 함께 release tarball 생성 → private S3의 `releases/<SHA>/release.tar.gz`에 업로드.
   - 배포: SSM Run Command가 `deploy/ssm-bootstrap.sh`와 `deploy/remote-deploy.sh`를 실행한다. EC2는 instance role로 ECR/S3에 접근하므로 GitHub runner가 SSH로 서버에 접속하지 않는다.
   - 순서는 **백엔드 배포 및 `/api/auth/me` 401 확인 → 프론트 교체 및 번들 해시 확인**으로 고정한다. 새 백엔드+구 프론트는 필드 추가에 안전하지만 그 반대는 안전하지 않다(`DEPLOY_LOG_2026-08-20.md` §1.2).
3. **배포 안전장치**:
   - 운영 배포 concurrency는 하나로 직렬화하며 실행 중인 배포를 취소하지 않는다.
   - 서버 `.env`와 현재 dist/config를 `~/office-commute/backups/<UTC>-<SHA>/`에 먼저 보존한다. `.env` 내용은 Actions/SSM 출력에 노출하지 않는다.
   - 새 백엔드가 401 스모크를 통과하기 전에는 프론트를 바꾸지 않는다. 프론트 검증 실패는 이전 dist로 자동 복구한다.
   - 백엔드 검증 실패는 자동 롤백하지 않는다. Flyway가 이미 적용됐을 수 있어 이전 앱으로 되돌리는 판단은 마이그레이션 호환성을 확인한 사람이 해야 한다.

### 3.1 CD 사전 조건

- EC2 instance role: `AmazonSSMManagedInstanceCore` + 해당 ECR repository pull + 배포 S3 prefix read.
- GitHub deploy role: OIDC trust를 `repo:limhjun/office-commute:ref:refs/heads/main`으로 제한 + 해당 ECR repository `DescribeImages`/push + 배포 S3 prefix write + 운영 EC2 한 대에 대한 `ssm:SendCommand`.
- EC2 필수 명령: `aws`, `docker`, Docker Compose plugin, `curl`, `rsync`, `flock`.
- GitHub Actions Repository variables:
  `AWS_REGION`, `AWS_ROLE_ARN`, `ECR_REGISTRY`, `ECR_REPOSITORY`, `DEPLOY_BUCKET`,
  `EC2_INSTANCE_ID`, `DEPLOY_DOMAIN`, `DEPLOY_PATH`, `CD_ENABLED`.
- `CD_ENABLED`는 첫 운영 검증 전 `false`로 두고, 준비가 끝난 뒤 정확히 `true`로 바꾼다. 값이 없거나 다른 문자열이면 자동/수동 배포 job을 모두 건너뛴다.
- 서버의 `~/office-commute/.env`는 기존처럼 유지한다. CD는 `APP_IMAGE` 한 줄만 새 ECR SHA 태그로 바꾸고 나머지 값을 복사·출력하지 않는다.

> ⚠️ Flyway 마이그레이션이 포함된 릴리스는 앱 롤백만으로 스키마가 돌아가지 않는다. 마이그레이션은 하위 호환(additive)으로 작성하고, 파괴적 변경(컬럼 drop 등)은 앱 배포와 분리해 한 릴리스 뒤에 적용한다 (V3, V6, V10 스타일의 drop은 특히 주의).

## 4. 환경변수 / 시크릿

`deploy/docker-compose.prod.yml`이 주입하는 전체 목록 기준:

| 변수 | 값 (prod) | 비고 |
|---|---|---|
| `APP_IMAGE` | `<레지스트리>/office-commute:<커밋 SHA>` | 롤백 메커니즘의 핵심(§3). 필수 — `latest` fallback은 롤백 전략과 모순이라 두지 않는다 |
| `SPRING_PROFILES_ACTIVE` | `prod` | compose가 고정 주입 |
| `DB_URL` | `jdbc:mysql://<RDS 엔드포인트>:3306/office_commute?serverTimezone=Asia/Seoul&characterEncoding=UTF-8` | 로컬 프로파일의 `useSSL=false&allowPublicKeyRetrieval=true`는 운영에 복사하지 않는다 |
| `DB_USERNAME` / `DB_PASSWORD` | 운영 DB | `DB_PASSWORD`는 기본값 없음 — 미설정 시 부팅 실패가 정상 |
| `PUBLIC_API_SERVICE_KEY` | 공공데이터포털 키 | 미설정 시 부팅 실패 — 배포 전 발급 확인 |
| `ADMIN_PASSWORD_HASH` | BCrypt 해시 | §1-4 (V11). 미설정 시 부팅 실패가 정상 |
| `SERVER_PORT` | 8080 (컨테이너 내부) | 기본값 그대로 |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | 사내 SMTP 또는 Gmail 앱 비밀번호 | ADR 3. prod 기본값 없음 — 미설정 시 부팅 실패 |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | `true` | 선택 — 미설정 시 둘 다 true |
| `REPORT_MAIL_FROM` | 발신 주소 | prod 기본값 없음 |
| `REPORT_MAIL_CEO` | 대표 메일 | 정상 리포트의 유일한 수신자. 스모크 시 임시 교체 주의(§5-9) |
| `REPORT_MAIL_MANAGERS` | 근태 관리자 메일 | 쉼표 구분 복수 가능. 미마감 보류·발송 실패 알림 수신자 |
| `PUBLIC_API_CONNECT_TIMEOUT` / `PUBLIC_API_READ_TIMEOUT` | `3s` / `5s` | 선택 — 배치가 공휴일 API에 매달려 재시도 창을 잡아먹지 않도록 재배포 없이 조정 |

> 메일 설정에 기본값을 두지 않는 이유: 리포트 배치는 매월 1~3일의 재시도 창(06/10/14/18시 KST + 3일 20시 최종 미발송 알림)에서만 돈다. 값이 비어 있어도 앱이
> 뜨면 다음 달 초 "리포트가 안 왔다"로 발견되고, 그 시점엔 이미 급여 정산이 지나 있다.
> `ADMIN_PASSWORD_HASH`와 같은 방침으로 배포 시점에 실패시킨다.

> ⚠️ **fail-fast의 실제 담당은 compose의 `${VAR:?}`다.** Spring의 "placeholder 기본값 없음"은 변수가 *아예 없을 때만* 부팅을 막는데,
> compose는 미설정 변수를 **빈 문자열로 존재하게** 주입해 이를 무력화한다 (실측: 필수 변수를 전부 비워도 `compose config`가 exit 0으로 통과,
> `ADMIN_PASSWORD_HASH=""`면 V11이 빈 해시를 심어 admin이 조용히 잠긴다). 그래서 `deploy/docker-compose.prod.yml`이 필수 변수 전부에
> `${VAR:?...}`를 걸어 누락·빈 값이면 `docker compose up` 자체를 거부한다.

운영 애플리케이션 시크릿은 서버의 `.env`(compose 파일 옆, gitignored)에만 유지한다. GitHub Actions에는 비시크릿 Repository variables만 두고 AWS 인증은 OIDC 단기 자격증명을 사용한다. docker-compose.yml의 로컬용 고정 비밀번호(`office_password` 등)를 운영에 재사용하지 않는다.

`.env` 값은 **작은따옴표로 감싼다.** compose는 `.env` 안에서도 `$` 변수 보간을 수행하므로, 따옴표 없이
`ADMIN_PASSWORD_HASH=$2a$10$abc...`로 쓰면 `$abc...`가 (존재하지 않는) 변수로 해석돼 **해시가 조용히 잘린다**
(실측 재현됨 — 값이 "존재"하므로 `:?`로도 잡히지 않는 손상이다). 올바른 형태:

```bash
ADMIN_PASSWORD_HASH='$2a$10$N9qo8uLOickgx2ZMRZoMye...'
```

## 5. 배포 당일 체크리스트

1. `./gradlew check` 통과 + `pnpm build` 통과. (`openapi.yml`을 건드린 커밋이 있다면 `schema.d.ts`가 같은 커밋에서 재생성되었는지 확인 — 현재는 정합.)
2. 로컬 풀스택 리허설: `./gradlew bootJar` → `docker compose --profile full up` → `http://localhost`에서 로그인·라우팅 확인 (§2 리허설 구성).
3. 운영 DB 생성(빈 스키마) → 앱 첫 기동 시 Flyway가 V1부터 적용되는지 로그 확인. (`baseline-on-migrate: true`는 빈 DB에선 무해하나, 기존 수동 스키마가 있는 DB에 물리면 마이그레이션이 스킵되므로 반드시 빈 DB에서 시작.)
4. `ADMIN_PASSWORD_HASH` 주입 확인 (`.env`에서 작은따옴표로 감쌌는지 — §4) → 배포 후 `admin1234`로 로그인이 **실패**하고, 새 비밀번호로는 **성공**하는 것 확인. (새 비밀번호 로그인까지 확인해야 해시 손상·오입력을 잡는다.)
5. HTTPS로 로그인 → `Set-Cookie`에 `Secure; HttpOnly; SameSite=Lax` 확인.
6. 비로그인 상태에서 루트 접속 → 로그인 페이지 정상 로드(정적 자산 401 없음) 확인. (app 기동 완료 전 수십 초간 `/api`가 502인 것은 정상 — §6 헬스체크 항목.)
7. SPA 라우트(`/teams`, `/employees`, `/overtime`) 직접 접속/새로고침 정상 확인.
8. **읽기 전용 스모크** — ⚠️ `scripts/api_test.sh`는 운영에 돌리지 않는다: admin 비밀번호가 `admin1234`로 하드코딩되어 로테이션 후 로그인부터 실패하고, 팀·직원·출퇴근·연차를 생성만 하고 지우지 않아 운영 DB에 테스트 데이터가 영구 잔류한다 (로컬 리허설 전용 — 승격 조건은 §6). 대신 curl로:
   - 비로그인 `GET /api/team` → 401 envelope 확인
   - 로그인 → `GET /api/auth/me`, `GET /api/team`, `GET /api/employee` → 200 확인
9. **메일 스모크** — ⚠️ 지난달 발송 이력이 없는 신규 DB에서는 이 호출이 **실제 수신자에게 (빈) 리포트를 발송한다**. `REPORT_MAIL_CEO`를 운영자 주소로 임시 교체 후: `POST /api/overtime/report/dispatch?yearMonth=<지난달>`을 MANAGER로 호출해 응답 `status`가 `SENT`(또는 미마감이 있으면 `FAILED` + `UNCLOSED_COMMUTES`)인지 확인 → 실주소로 되돌리고 app 재기동. 배치 첫 발화(다음 달 1일 06:00 KST)를 기다리지 않고 발송 경로 전체를 검증하는 방법이다. 같은 달 재호출은 멱등(no-op)이라 안전하다.
10. DB 백업 크론 + 로그 로테이션(`app-logs` 볼륨의 `office-commute.log`, 설정상 100MB/30일) 동작 확인.
11. certbot 갱신 리허설: `certbot renew --dry-run` (webroot 설정 — §2.1-5).

## 6. 배포 후 개선 로드맵 (권고 — 차단 아님)

| 우선순위 | 항목 | 근거 |
|---|---|---|
| 높음 | 로그인 실패 메시지 통일 + 더미 해시 매칭 | 현재 "존재하지 않는 이메일" vs "비밀번호 불일치" 구분 응답 → 계정 열거 가능 |
| 높음 | 로그인 rate limit (Nginx `limit_req`로 즉시 가능) | 브루트포스 방어 부재, 고정 admin 계정과 결합 시 위험 |
| 높음 | 프론트 401 전역 처리 → 로그인 리다이렉트 | 세션 만료(기본 30분) 시 토스트만 반복되는 UX (검토 실증) |
| 중간 | 비밀번호 변경 API | 현재 로테이션 수단이 DB 직접 수정뿐 (V11은 1회성) |
| 중간 | `spring-boot-starter-actuator` 추가, `/actuator/health`만 노출 → compose `app` 헬스체크 + nginx `depends_on: condition: service_healthy` | 헬스체크/모니터링 기반 (현재 미포함). 기동 중 502 창 제거 |
| 중간 | `scripts/api_test.sh`에 자격증명 env 주입 + 생성 데이터 cleanup | 갖춰지면 운영 스모크로 승격 가능 (§5-8) |
| 중간 | XML 컨버터 제거 또는 JSON 강제 | `jackson-dataformat-xml`로 콘텐츠 협상 시 XML 에러 노출 (검토 실증) |
| 중간 | `GET /commute` 전용 응답 DTO + 스펙 정합 (`workingMinutes`), `non_null` inclusion 재검토 | 스펙 드리프트 2건 (검토 실증) |
| 낮음 | `jar { enabled = false }` (plain jar 생성 차단) | `./gradlew build` 후 Dockerfile `COPY build/libs/*.jar`가 두 파일에 매칭돼 깨지는 함정 제거 (§3) |
| 낮음 | Nginx HSTS 헤더 + gzip(정적 자산) | 보안 헤더·전송량 — 트래픽상 급하지 않음 |
| 낮음 | Spring Session + Redis | 재배포 시 전원 로그아웃 해소 + 수평 확장 전제. 트래픽상 당장 불필요 |
| 낮음 | Spring Boot 4.x 업그레이드 계획 | 3.5는 2026년 중반 OSS 지원 종료 라인 — 정확한 일정은 spring.io 지원 표에서 확인 후 분기 내 계획 수립 권장 |

## 7. 검증에서 "이상 없음"으로 확인된 범위 (2026-07-09, 2026-08-14 재확인)

- **API 계약**: 전 컨트롤러 경로/메서드/상태코드/필드/에러 envelope가 `openapi.yml`과 일치 (dev 프로파일 실기동 + curl 실증). 날짜 직렬화(`yyyy-MM-dd`, `yyyy-MM`), enum(Role), 빈 200 응답 처리 정상. `schema.d.ts`는 `openapi.yml`과 같은 커밋에서 재생성 유지 중.
- **인가 구조**: `AuthInterceptor` + `@ManagerOnly` + 세션 `currentEmployeeId` 조합 견고. IDOR 없음(본인 리소스는 세션 ID만 사용), 경로 매칭 우회 없음, 세션 고정 방어 구현·테스트 존재, 응답에 비밀번호 해시 미노출. 수동 dispatch 엔드포인트도 `@ManagerOnly`.
- **프론트 인증 처리**: 토큰을 localStorage에 두지 않음(세션 쿠키 + `credentials: 'include'`) — XSS로 탈취될 자격증명 없음.
- **CI 서술 정합**: §3의 CI 설명은 `.github/workflows/ci.yml` 실물과 일치 (잡 이름, required check 주의사항 포함).
- **발송 멱등성**: `report_dispatch`의 `UNIQUE(target_year_month)` + 종착 상태(`SENT`/`DELIVERY_COMMITTED`)로 재발송 차단 — §5-9 스모크의 전제 코드로 확인.
