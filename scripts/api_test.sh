#!/bin/bash

# ▒▒▒ OS 호환 날짜 계산 (Python 사용) ▒▒▒
# Python을 사용하여 날짜를 계산하므로 macOS와 Linux에서 모두 동일하게 작동합니다.
get_date() {
    local offset="$1"
    # Python을 실행하여 날짜 계산 후 결과를 받아옵니다.
    python3 -c "from datetime import date, timedelta; print(date.today() + timedelta(days=${offset}))"
}

# ▒▒▒ 실패 추적 및 테스트 래퍼 ▒▒▒
errors=()
passed_count=0
failed_count=0

# 테스트 실행 및 결과 기록 함수
do_test_step() {
  description=$1
  shift
  echo "-----------------------------------------------------"
  echo "🚀 Executing: $description"
  echo "-----------------------------------------------------"
  if "$@"; then
    echo "✅ SUCCESS: $description"
    passed_count=$((passed_count + 1))
  else
    echo "❌ FAILED: $description"
    errors+=("$description")
    failed_count=$((failed_count + 1))
  fi
  echo
}

# ▒▒▒ 날짜 변수 설정 ▒▒▒
today=$(get_date "0")
year_month=$(date +%Y-%m) # 이 형식은 macOS와 Linux에서 동일하게 작동합니다.

# 미래 연차 날짜 (today 기준 +5일, +6일, +7일)
future_date1=$(get_date "+5")
future_date2=$(get_date "+6")
future_date3=$(get_date "+7")

echo "✅ Today's Date: $today"
echo "✅ Future leave dates: $future_date1, $future_date2, $future_date3"
echo "✅ Past leave dates: $past_date1, $past_date2"
echo "✅ Current Year-Month: $year_month"
echo

# ▒▒▒ 서버 및 세션 정보 ▒▒▒
base_url="http://localhost:8080"
# 세션을 유지하기 위한 httpie의 세션 파일 이름
SESSION="api-test-session"

# 모든 http 호출에 공통 플래그를 강제하기 위해 셸 함수로 감싼다.
#   --ignore-stdin : 비대화형 셸(CI, 파이프)에서 httpie가 stdin을 본문으로 오인해 죽는 것을 방지
#   --check-status : 4xx/5xx 응답을 exit 비0으로 처리 → do_test_step이 실제 실패를 잡을 수 있도록
http() {
  command http --ignore-stdin --check-status "$@"
}

# === API 함수 정의 ===
# API 호출 시 verbose(-v) 옵션은 디버깅 시 유용하지만, 테스트 결과만 깔끔하게 보려면 제거하는 것이 좋습니다.
# 여기서는 그대로 유지합니다.
login_admin() {
  http -v --session="$SESSION" POST "$base_url/api/auth/login" \
      email="admin@company.com" \
      password="admin1234"
}

# 생성 응답의 ID를 다음 단계로 넘기기 위한 전역 변수.
# do_test_step 래퍼가 종료 코드만 보므로, 함수 외부에서 캡처해야 한다.
LAST_TEAM_ID=""
LAST_EMPLOYEE_ID=""

create_team() {
  team_name=$1
  # --print=b : 응답 본문만 stdout → jq로 깔끔히 파싱 가능
  local response
  response=$(http --print=b --session="$SESSION" POST "$base_url/team" teamName="$team_name") || return 1
  echo "$response"
  LAST_TEAM_ID=$(echo "$response" | jq -r '.teamId')
}

create_employee() {
  name=$1
  role=$2
  birthday=$3
  work_start_date=$4
  employee_code=$5
  email=$6
  password=$7
  local response
  response=$(http --print=b --session="$SESSION" POST "$base_url/employee" \
    name="$name" \
    role="$role" \
    birthday="$birthday" \
    workStartDate="$work_start_date" \
    employeeCode="$employee_code" \
    email="$email" \
    password="$password") || return 1
  echo "$response"
  LAST_EMPLOYEE_ID=$(echo "$response" | jq -r '.employeeId')
}

assign_employee_to_team() {
  employee_id=$1
  team_id=$2
  http -v --session="$SESSION" PUT "$base_url/employee/$employee_id/team" teamId:="$team_id"
}

login_employee() {
  email=$1
  password=$2
  http -v --session="$SESSION" POST "$base_url/api/auth/login" \
      email="$email" \
      password="$password"
}

register_work_start_time() {
  http -v --session="$SESSION" POST "$base_url/commute"
}

register_work_end_time() {
  http -v --session="$SESSION" PUT "$base_url/commute"
}

request_annual_leave() {
  # JSON 배열을 직접 인라인으로 전달합니다.
  wanted_dates=$1
  http -v --session="$SESSION" POST "$base_url/annual-leave" wantedDates:="$wanted_dates"
}

get_remaining_annual_leave() {
  http -v --session="$SESSION" GET "$base_url/annual-leave"
}

get_work_duration_per_date() {
  year_month=$1
  http -v --session="$SESSION" GET "$base_url/commute?yearMonth=$year_month"
}

# === API 테스트 실행 ===
do_test_step "관리자 로그인" login_admin

do_test_step "팀 생성 (백엔드)" create_team "백엔드"
backend_team_id="$LAST_TEAM_ID"

do_test_step "임형준 사원 생성" create_employee "임형준" "MANAGER" "1995-05-15" "$today" "EMP001" "emp001@company.com" "password123!"
hyungjun_employee_id="$LAST_EMPLOYEE_ID"
do_test_step "고슬링 사원 생성" create_employee "고슬링" "MEMBER" "1950-05-15" "$today" "EMP002" "emp002@company.com" "password123!"
gosling_employee_id="$LAST_EMPLOYEE_ID"
do_test_step "존카맥 사원 생성" create_employee "존카맥" "MEMBER" "1960-05-15" "$today" "EMP003" "emp003@company.com" "password123!"

# 두 사원을 백엔드 팀에 배정. 임형준이 본인 자격으로 출퇴근/연차를 신청하므로
# 임형준에게도 팀이 있어야 EMPLOYEE_WITHOUT_TEAM 위반이 발생하지 않는다.
do_test_step "팀 배정 (임형준 to 백엔드)" assign_employee_to_team "$hyungjun_employee_id" "$backend_team_id"
do_test_step "팀 배정 (고슬링 to 백엔드)" assign_employee_to_team "$gosling_employee_id" "$backend_team_id"

do_test_step "사원 로그인 (임형준)" login_employee "emp001@company.com" "password123!"

do_test_step "출근 등록" register_work_start_time
# 실제 테스트 시나리오를 위해 출퇴근 사이에 약간의 시간 간격을 둡니다.
sleep 2
do_test_step "퇴근 등록" register_work_end_time

# JSON 배열 형식으로 날짜를 전달합니다.
do_test_step "미래 연차 신청" request_annual_leave "[\"$future_date1\", \"$future_date2\", \"$future_date3\"]"

do_test_step "남은 연차 조회" get_remaining_annual_leave
do_test_step "월별 근무 시간 조회" get_work_duration_per_date "$year_month"


# === 테스트 결과 리포트 ===
echo
echo "========================================="
echo "🧪           TEST REPORT           🧪"
echo "========================================="

echo "✅ Passed: $passed_count"
echo "❌ Failed: $failed_count"
echo

if [ "$failed_count" -gt 0 ]; then
  echo "--- Failed Steps ---"
  for err in "${errors[@]}"; do
    echo "  - $err"
  done
  echo
  echo "========================================="
  echo "🚨            RESULT: FAILED            🚨"
  echo "========================================="
  exit 1
else
  echo "========================================="
  echo "🎉        RESULT: ALL TESTS PASSED       🎉"
  echo "========================================="
  exit 0
fi
