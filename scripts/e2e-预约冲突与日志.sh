#!/usr/bin/env bash
# 预约冲突 / 审批 / 日志 多账号端到端校验
#
# 运行前提：
#   1. 后端已在 127.0.0.1:8080 运行（./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8080）
#   2. 演示账号待审队列中不要有其它并存申请，否则"挑空闲时段"可能会跳过
#   3. 账号密码从仓库根 `.env` 读取，变量见 scripts/README.md；脚本本身不含任何密码
#
# 用法：bash scripts/e2e-预约冲突与日志.sh
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ -f "$ROOT/.env" ]; then
  set -a; . "$ROOT/.env"; set +a
fi

: "${E2E_STUDENT_PASS:?缺少 E2E_STUDENT_PASS，请在仓库根 .env 中设置，见 scripts/README.md}"
: "${E2E_COUNSELOR_PASS:?缺少 E2E_COUNSELOR_PASS，见 scripts/README.md}"
: "${E2E_REVIEWER_PASS:?缺少 E2E_REVIEWER_PASS，见 scripts/README.md}"
: "${E2E_ADMIN_PASS:?缺少 E2E_ADMIN_PASS，见 scripts/README.md}"

BASE="${E2E_BASE:-http://127.0.0.1:8080/api/v1}"
PASS=0; FAIL=0
ok(){ echo "  [PASS] $1"; PASS=$((PASS+1)); }
no(){ echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }
eq(){ if [ "$2" = "$3" ]; then ok "$1 = $2"; else no "$1 期望 $3 实际 $2"; fi }

login(){
  local u=$1 p=$2 jar=/tmp/jar_$1
  rm -f "$jar"
  curl -s --noproxy '*' -c "$jar" "$BASE/auth/csrf" > /tmp/csrf_$u.json
  local tok=$(sed -E 's/.*"token":"([^"]+)".*/\1/' /tmp/csrf_$u.json)
  local hdr=$(sed -E 's/.*"headerName":"([^"]+)".*/\1/' /tmp/csrf_$u.json)
  curl -s --noproxy '*' -o /dev/null -w "%{http_code}" -b "$jar" -c "$jar" -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" -H "$hdr: $tok" \
    -d "{\"loginName\":\"$u\",\"password\":\"$p\"}"
}

# 写操作需要新鲜 CSRF：输出 "头名|令牌"
csrf(){
  local u=$1 jar=/tmp/jar_$u
  curl -s --noproxy '*' -b "$jar" -c "$jar" "$BASE/auth/csrf" > /tmp/csrf_$u.json
  local tok=$(sed -E 's/.*"token":"([^"]+)".*/\1/' /tmp/csrf_$u.json)
  local hdr=$(sed -E 's/.*"headerName":"([^"]+)".*/\1/' /tmp/csrf_$u.json)
  echo "$hdr|$tok"
}

send(){ # 方法 用户 路径 响应体 额外头...
  local method=$1 u=$2 path=$3 body=${4:-}
  local cs=$(csrf "$u"); local hdr=${cs%%|*}; local tok=${cs##*|}
  local args=(-s --noproxy '*' -b "/tmp/jar_$u" -c "/tmp/jar_$u" -X "$method" "$BASE$path"
              -H "Content-Type: application/json" -H "$hdr: $tok")
  shift 4 2>/dev/null || shift $#
  for h in "$@"; do args+=(-H "$h"); done
  [ -n "$body" ] && args+=(-d "$body")
  local out=$(curl "${args[@]}" -w $'\n%{http_code}')
  CODE=$(echo "$out" | tail -1); BODY=$(echo "$out" | sed '$d')
}

getjson(){ # 用户 路径 -> 设置 CODE/BODY
  local u=$1 path=$2
  local out=$(curl -s --noproxy '*' -b "/tmp/jar_$u" "$BASE$path" -w $'\n%{http_code}')
  CODE=$(echo "$out" | tail -1); BODY=$(echo "$out" | sed '$d')
}

jget(){ echo "$1" | sed -E "s/.*\"$2\":(\"?)([^\",}]*)\1.*/\2/"; }

echo "== 1. 登录四个账号 =="
for pair in "student01 $E2E_STUDENT_PASS" "counselor01 $E2E_COUNSELOR_PASS" "reviewer01 $E2E_REVIEWER_PASS" "admin $E2E_ADMIN_PASS"; do
  set -- $pair; c=$(login "$1" "$2"); eq "$1 登录" "$c" "200"
done

echo "== 2. 挑一个空闲时段（明天整点） =="
DAY=$(date -d tomorrow +%Y-%m-%d)
SPACE=1
SLOT=""; START=""; END=""
for H in 09 10 11 13 14 15 16 17 18 19 20; do
  H2=$((10#$H+1)); H2=$(printf "%02d" $H2)
  S="${DAY}T$H:00:00+08:00"; E="${DAY}T$H2:00:00+08:00"
  send POST student01 /reservations "{\"resourceType\":\"SPACE\",\"resourceId\":$SPACE,\"startAt\":\"$S\",\"endAt\":\"$E\",\"purpose\":\"冲突校验-学生\",\"quantity\":1}" "Idempotency-Key: e2e-stu-$H-$$"
  if [ "$CODE" = "201" ]; then SLOT=$H; START=$S; END=$E; STU_ID=$(jget "$BODY" id); STUDENT_OK=1; break; fi
done
if [ -n "$SLOT" ]; then ok "学生提交 $DAY $SLOT:00 成功 (id=$STU_ID)"; else no "没有可用时段，后续检查跳过"; fi

if [ -n "$SLOT" ]; then
  echo "== 3. 辅导员提交同一时段 =="
  send POST counselor01 /reservations "{\"resourceType\":\"SPACE\",\"resourceId\":$SPACE,\"startAt\":\"$START\",\"endAt\":\"$END\",\"purpose\":\"冲突校验-辅导员\",\"quantity\":1}" "Idempotency-Key: e2e-cou-$SLOT-$$"
  eq "辅导员同段提交" "$CODE" "201"; COU_ID=$(jget "$BODY" id)
  eq "辅导员申请状态" "$(jget "$BODY" status)" "PENDING_APPROVAL"

  echo "== 4. 待审队列：顺序与竞争提示 =="
  # 主线定稿：队列按提交顺序，不按申请人身份区分，priorityTier 恒为 STANDARD
  getjson reviewer01 /review/reservations
  eq "队列读取" "$CODE" "200"
  STU_TIER=$(echo "$BODY" | sed -E "s/.*\"id\":$STU_ID[^}]*\"priorityTier\":\"([A-Z]+)\".*/\1/")
  COU_TIER=$(echo "$BODY" | sed -E "s/.*\"id\":$COU_ID[^}]*\"priorityTier\":\"([A-Z]+)\".*/\1/")
  eq "学生标签" "$STU_TIER" "STANDARD"
  eq "辅导员标签（不区分身份）" "$COU_TIER" "STANDARD"

  echo "== 5. 不可自审 =="
  send PATCH student01 "/reservations/$STU_ID/review" '{"decision":"APPROVE"}'
  eq "学生审自己" "$CODE" "409"; eq "错误码" "$(jget "$BODY" code)" "SELF_APPROVAL_FORBIDDEN"

  echo "== 6. 审核人选择学生（辅导员仍在待审） =="
  send PATCH reviewer01 "/reservations/$STU_ID/review" '{"decision":"APPROVE"}'
  eq "批准学生" "$CODE" "200"; eq "学生状态" "$(jget "$BODY" status)" "CONFIRMED"

  echo "== 7. 再批辅导员应被时段占用挡住 =="
  send PATCH reviewer01 "/reservations/$COU_ID/review" '{"decision":"APPROVE"}'
  eq "再批辅导员" "$CODE" "409"; eq "错误码" "$(jget "$BODY" code)" "RESOURCE_CONFLICT"

  echo "== 8. 重复审核同一单 =="
  send PATCH reviewer01 "/reservations/$STU_ID/review" '{"decision":"APPROVE"}'
  eq "重复审核" "$CODE" "409"; eq "错误码" "$(jget "$BODY" code)" "INVALID_STATE"
fi

echo "== 9. 日志模块：审核人与结论 =="
getjson reviewer01 "/management/reservations?status=CONFIRMED&keyword=student01&size=50"
eq "日志筛选读取" "$CODE" "200"
if echo "$BODY" | grep -q '"reviewerName"'; then ok "日志返回 reviewerName 字段"; else no "日志缺 reviewerName"; fi
if echo "$BODY" | grep -q '"decision":"APPROVED"'; then ok "日志含处理结论 APPROVED"; else no "日志缺处理结论"; fi
getjson reviewer01 "/management/reservations?status=NOT_A_STATUS"
eq "非法状态筛选" "$CODE" "400"
getjson student01 "/management/reservations"
eq "普通学生读日志" "$CODE" "403"

echo "== 10. 并发：两名审核员同时批准同一单 =="
DAY2=$(date -d tomorrow +%Y-%m-%d)
CONC=""
for H in 09 10 11 13 14 15; do
  H2=$(printf "%02d" $((10#$H+1)))
  S="${DAY2}T$H:00:00+08:00"; E="${DAY2}T$H2:00:00+08:00"
  send POST student01 /reservations "{\"resourceType\":\"SPACE\",\"resourceId\":$SPACE,\"startAt\":\"$S\",\"endAt\":\"$E\",\"purpose\":\"并发同单\",\"quantity\":1}" "Idempotency-Key: e2e-conc-$H-$$"
  if [ "$CODE" = "201" ]; then CONC=$(jget "$BODY" id); break; fi
done
if [ -n "$CONC" ]; then
  ( send PATCH reviewer01 "/reservations/$CONC/review" '{"decision":"APPROVE"}'; echo "$CODE" > /tmp/p1 ) &
  ( send PATCH admin "/reservations/$CONC/review" '{"decision":"APPROVE"}'; echo "$CODE" > /tmp/p2 ) &
  wait
  C1=$(cat /tmp/p1); C2=$(cat /tmp/p2)
  OKS=$(printf "%s\n%s\n" "$C1" "$C2" | grep -c '^200$')
  CONFL=$(printf "%s\n%s\n" "$C1" "$C2" | grep -c '^409$')
  eq "成功次数" "$OKS" "1"; eq "冲突次数" "$CONFL" "1"
  echo "     实际返回：$C1 / $C2"
else
  no "并发场景没拿到可用时段"
fi

echo
echo "== 结果：通过 $PASS 项，失败 $FAIL 项 =="
[ "$FAIL" -eq 0 ] || exit 1
