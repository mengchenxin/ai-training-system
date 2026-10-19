#!/usr/bin/env bash
# 通过 Vite dev server 逐个请求路由页面模块，验证 SFC 在开发模式下能正常编译。
# 只检查当前路由实际引用的页面（reachable set）。

cd "$(dirname "$0")/.." || exit 1

BASE="http://127.0.0.1:3000"
ok=0
bad=0

# 从 router/index.ts 抽取所有 @/views/... 与 @/layout/... 引用
files=$(grep -oE '@/views/[A-Za-z0-9_/-]+\.vue|@/layout/[A-Za-z0-9_/-]+\.vue' src/router/index.ts | sort -u)

echo "待验证页面模块："
echo "$files" | sed 's|@/|  src/|'
echo

for alias in $files; do
    rel="src/${alias#@/}"
    body=".page-check.tmp"
    code=$(curl -s -o "$body" -w "%{http_code}" --max-time 15 "$BASE/$rel")
    if [ "$code" = "200" ] && ! grep -qiE "Internal server error|Failed to parse|SyntaxError|Transform failed" "$body"; then
        ok=$((ok + 1))
        printf "  [OK]   %s\n" "$rel"
    else
        bad=$((bad + 1))
        printf "  [FAIL] %s  (HTTP %s)\n" "$rel" "$code"
        head -c 300 "$body" | sed 's/^/         /'
        echo
    fi
done

echo
echo "结果：成功 $ok 个，失败 $bad 个"
[ "$bad" -eq 0 ] || exit 1
