# 퇴근 시각 정정·월 마감 운영 안내

- 정책: [COMMUTE_CORRECTION_PLAN.md](COMMUTE_CORRECTION_PLAN.md)
- API 계약: [COMMUTE_CORRECTION_API.md](COMMUTE_CORRECTION_API.md), `openapi.yml`
- 마이그레이션: `V15__commute_correction.sql`, `V16__monthly_closing_and_report_file.sql`

## 1. 배포 시 확인

- V16은 `report_dispatch`의 기존 유니크 인덱스 `uk_report_dispatch_year_month`를 `uk_report_dispatch_year_month_kind`(월+종류)로 교체한다. 기존 발송 이력은 모두 `kind = ORIGINAL`이 된다.
- 운영 프로파일은 `ddl-auto: validate`다. 두 마이그레이션이 적용되지 않으면 애플리케이션이 기동하지 않는다.
- 배포 후 첫 예약 발송(매월 1~3일)부터는 **월 마감 전이면 발송하지 않는다**(`MONTH_NOT_CLOSED`). 근태 관리자가 전월을 마감하면 그 요청 안에서 발송이 이어진다. 마감이 늦어지면 3일 20:00 미발송 알림이 나간다.

## 2. 최종 보고서 보관

| 항목 | 내용 |
| --- | --- |
| 저장 위치 | DB `report_file` 테이블의 `content`(LONGBLOB). 월·종류(`ORIGINAL`/`CORRECTION`)마다 한 행이며, 덮어쓰지 않는다. |
| 파일 식별 | `(target_year_month, kind)` UNIQUE, `sha256`, `size_bytes`, `created_at` |
| 생성 시점 | 마감된 월의 최초 발송 시도. 파일 저장이 성공해야 발송한다. 재시도와 확정본 다운로드는 이 파일을 그대로 쓴다. |
| 접근 권한 | `GET /api/overtime/report/final-excel` (MANAGER, 세션 인증). 공개 URL은 없다. |
| 백업 | DB 백업에 포함된다. 별도 파일 스토리지는 없다. DB 백업 보존 기간이 곧 보관본 보존 기간이다. |

`GET /api/overtime/report/excel`은 현재 데이터로 다시 집계하는 **참고용**이다(파일명 `_참고용`). 확정본을 대신하지 않는다.

## 3. 수신 여부 불명(DELIVERY_COMMITTED)

SMTP 호출 후 결과를 기록하지 못한 발송은 자동으로 다시 보내지 않는다. 운영자가 수신자에게 수신 여부를 확인한 뒤 다음 API로 기록한다.

```
POST /api/overtime/report/dispatch/confirmation
{ "yearMonth": "2026-08", "kind": "ORIGINAL", "outcome": "DELIVERED" | "NOT_DELIVERED", "note": "확인 근거" }
```

- `DELIVERED`: SENT로 확정한다. 재발송하지 않는다.
- `NOT_DELIVERED`: FAILED로 돌린다. 이후 수동·예약 발송이 보관 파일로 다시 보낸다(마감 전이면 마감 후).
- 확인 전까지 그 월의 집계 입력 기간에는 근태 쓰기와 정정이 임시로 막힌다(`REPORT_DELIVERY_UNCERTAIN`).

## 4. 기능 도입 전 발송 월의 일회성 전환

대상은 원본이 이미 SENT인데 마감 정보가 없는 월이다(`GET /api/monthly-closings/{yearMonth}`의 `legacyDispatch = true`).

1. 기존 발송 상태, 시스템 밖 발송 여부, 원본 파일 확보 여부, 정정 대상을 확인한다. 원본 파일은 시스템이 재생성하지 않는다. 발송 메일이나 별도 보관본에서 확보하고, 확보 여부는 마감 `note`에 기록한다.
2. 집계 기간이 겹치는 월의 순서를 정한다. 예를 들어 9월의 입력 기간은 8/31~9/30이다. 이전 월이 미정리 기존 발송 월이면 다음 월 마감은 `CLOSING_PREVIOUS_LEGACY_MONTH_PENDING`으로 거부된다.
3. 필요한 정정을 신청·승인 절차로 처리한다(원본 직접 수정 금지).
4. 미퇴근·대기 요청이 없는 상태에서 마감한다.
   - 정정이 없으면 `legacyResolution: NO_CORRECTION_NEEDED`. 잠그기만 하고 재발송하지 않는다.
   - 정정했다면 `legacyResolution: CORRECTED`. 잠근 뒤 정정본(`kind = CORRECTION`, 파일명 `_정정본`)을 원본과 별도 이력으로 발송한다.
5. 발송이 실패해도 마감은 해제되지 않는다. `POST /api/overtime/report/dispatch?yearMonth=`로 재시도한다.

2026년 9월은 실제 정정이 필요하다고 확인된 월이다. 실제 대상 ID와 시각은 운영 데이터와 신청자 확인으로 특정한다.

## 5. 잠금 원칙(장애 분석용)

- 근태 쓰기(출근·퇴근·연차·정정 신청/승인/반려/취소)는 소유 직원의 `employee` 행을 `SELECT … FOR UPDATE`로 먼저 잠근다.
- 월 마감·퇴사일 지정·승인 담당자 지정은 전체 `employee` 행을 ID 오름차순으로 잠근다. 직원 수만큼의 행 잠금이므로 이 작업들은 짧게 끝난다.
- 역할은 매 요청 DB에서 다시 읽는다. 역할을 바꾼 직후에도 이전 세션의 역할로는 처리되지 않는다.
- 상태 변경 요청은 Origin/Referer가 같은 출처여야 한다(`CSRF_ORIGIN_REJECTED`). 개발 환경은 `app.security.allowed-origins`에 Vite 출처(`http://localhost:5173`)를 둔다.
