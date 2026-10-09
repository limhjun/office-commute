# 인계: commute-correction — Backend

- 작업 식별자: `commute-correction`
- 담당 영역: Backend (서버·DB·테스트) + API 계약(`openapi.yml`)·생성 타입(`frontend/src/api/schema.d.ts`)
- 상태: **WORKING**
- 작업 브랜치: `worktree-backend`
- 계약 커밋: (이 파일을 포함한 계약 커밋 — `git log --oneline -- openapi.yml`로 확인)
- 최종 구현 커밋 SHA: 미정 (구현 진행 중)

## 계약

- `docs/COMMUTE_CORRECTION_API.md`: 경로, 요청·응답 예시, 오류 코드, 역할별 권한, 잠금 원칙.
- 기존 계약 대비 프론트엔드가 반드시 반영해야 할 변경:
  - `CommuteDetail.status`의 `UNCLOSED` → `CORRECTION_REQUIRED`. 현재 `MyCommutePage.tsx`에서 `tsc`가 실패한다.
  - `PREVIOUS_COMMUTE_NOT_ENDED` 오류 코드 삭제.
  - 참고용 Excel 파일명 변경.

## 구현 진행

- [ ] V15/V16 마이그레이션, 엔티티
- [ ] 출퇴근 규칙 변경
- [ ] 정정 요청 신청·승인·반려·취소
- [ ] 승인 담당자·COMMUTE_APPROVER 권한, 역할 재조회, CSRF
- [ ] 월 마감, 보고서 보관·발송 연결
- [ ] 테스트, `./gradlew check`

## 검증

(구현 완료 후 기록)
