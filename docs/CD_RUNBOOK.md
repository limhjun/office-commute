# GitHub Actions CD 운영 가이드

> 대상: `.github/workflows/deploy.yml` · EC2 단일 서버 · ECR + S3 + SSM Run Command

## 자동 배포 흐름

1. `main` push로 `CI` workflow 실행.
2. backend/frontend CI가 모두 성공하면 `Deploy` workflow 시작.
3. GitHub OIDC로 `office-commute-github-deploy` 역할을 assume.
4. backend image를 ECR에 `<full commit SHA>` 태그로 push.
5. frontend dist와 운영 compose/nginx 파일을 tarball로 묶어 S3 `releases/<SHA>/`에 업로드.
6. SSM Run Command가 EC2에서 release를 내려받고 SHA-256을 검증.
7. 현재 `.env`, dist, compose/nginx 설정을 서버 `backups/`에 보존.
8. backend를 먼저 교체하고 `https://<domain>/api/auth/me`가 401인지 확인.
9. frontend dist를 교체하고 nginx config test/reload 후 index가 새 asset을 가리키는지 확인.

## 최초 실행 전 확인

GitHub Repository variable `CD_ENABLED`는 이 확인을 마칠 때까지 `false`로 둔다. 이렇게 하면 workflow 파일을 `main`에 먼저 합쳐도 운영 배포가 시작되지 않는다.

Session Manager에서 다음을 실행한다. 값이나 시크릿을 출력하지 않는다.

```bash
for command_name in aws docker curl rsync flock; do
  command -v "$command_name" || printf 'MISSING: %s\n' "$command_name"
done
docker compose version
sudo test -f /home/ubuntu/office-commute/.env
sudo docker compose \
  --env-file /home/ubuntu/office-commute/.env \
  -f /home/ubuntu/office-commute/docker-compose.prod.yml \
  -f /home/ubuntu/office-commute/docker-compose.mysql.yml \
  config --quiet
```

`aws`가 없으면 AWS CLI v2를, `rsync`가 없으면 Ubuntu 패키지를 설치한 뒤 다시 확인한다. 첫 자동 배포 전에는 SSH를 없애지 말고 운영자 IP `/32`로만 제한해 복구 경로로 남긴다.

확인이 끝나면 `CD_ENABLED=true`로 바꾸고 아래 수동 실행으로 첫 배포를 검증한다. 첫 성공 이후에는 `main` CI 성공 시 같은 workflow가 자동 실행된다.

## 수동 재실행

GitHub 저장소의 **Actions → Deploy → Run workflow**에서 `main`을 선택한다. workflow는 `main`이 아닌 ref의 수동 실행을 거부한다. ECR tag는 immutable이므로 동일 SHA의 backend image가 이미 존재하면 workflow는 다시 빌드·push하지 않고 그 이미지를 그대로 재사용한다. 이미지 내용을 바꿔야 한다면 새 커밋으로 실행한다.

## 실패 시 판단

- **OIDC 실패**: deploy role의 trust `sub`가 `repo:limhjun/office-commute:ref:refs/heads/main`인지 확인.
- **ECR push/pull 실패**: GitHub deploy role의 push policy(`DescribeImages` 포함)와 EC2 instance role의 pull policy를 각각 확인.
- **S3 실패**: GitHub는 `releases/*` PutObject, EC2는 같은 prefix GetObject 권한만 가져야 한다.
- **SSM SendCommand 실패**: role policy가 운영 instance ARN과 `AWS-RunShellScript` document ARN을 모두 허용하는지 확인.
- **required command is not installed**: Session Manager에서 해당 서버 명령을 설치한다.
- **backend 401 smoke 실패**: SSM 출력과 `docker compose logs app`을 확인한다. Flyway 때문에 자동 롤백하지 않는다. 서버 `backups/<UTC>-<SHA>/.env`에 이전 `APP_IMAGE`가 보존되어 있다.
- **frontend smoke 실패**: workflow가 이전 dist를 자동 복구한다. backend는 새 버전으로 유지되며, 이는 API 필드 추가에 대해 안전한 배포 순서다.

## 보관 정책

- ECR image와 S3 release key는 commit SHA로 불변 식별한다.
- S3 bucket versioning은 켜 두되, 비용이 계속 늘지 않도록 운영 안정화 후 lifecycle rule로 오래된 release/noncurrent version을 정리한다.
- 서버의 `releases/`와 `backups/`도 자동 삭제하지 않는다. 최근 정상 배포와 DB migration 호환성을 확인한 후 사람이 정리한다.
