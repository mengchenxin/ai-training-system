#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
并发压测脚本（对齐 work/app/jmeter/ai-training-test-plan.jmx 的三个场景）

用法：
    python load_test.py --base http://127.0.0.1:8000/api --concurrency 50 --tasks 300
    python load_test.py --scenario login --concurrency 20 --tasks 200

输出：每场景的 平均 / P50 / P90 / P95 / P99 / 最小 / 最大 / TPS / 错误率，
      并落盘 outputs/压测结果-<时间戳>.json 与 .md。
"""
import argparse
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

# 绕过系统/环境代理，直连本机
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def http(method, url, body=None, token=None, timeout=30, limit=200):
    """返回 (status_code, 耗时毫秒, 响应体前 200 字)。"""
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    t0 = time.perf_counter()
    try:
        with OPENER.open(req, timeout=timeout) as resp:
            raw = resp.read()
            code = resp.status
    except urllib.error.HTTPError as e:
        raw = e.read()
        code = e.code
    except Exception as e:  # 连接失败等
        return -1, (time.perf_counter() - t0) * 1000.0, str(e)[:200]
    cost = (time.perf_counter() - t0) * 1000.0
    return code, cost, raw[:limit].decode("utf-8", "replace")


def login(base):
    code, cost, body = http("POST", base + "/auth/login",
                            {"username": "admin", "password": "123456"})
    if code != 200:
        sys.exit("登录失败：%s %s" % (code, body))
    return json.loads(body)["data"]["token"]


def fetch_accounts(base, token, pages=8, size=10):
    """拉取可用账号名，用于登录场景的多账号轮询（避免同账号并发登录互踢）。"""
    names = []
    for p in range(1, pages + 1):
        code, _, body = http("GET", "%s/system/user/page?pageNum=%d&pageSize=%d"
                             % (base, p, size), None, token, limit=200000)
        if code != 200:
            break
        recs = json.loads(body).get("data", {}).get("records", [])
        if not recs:
            break
        names.extend([r["username"] for r in recs if r.get("status") in (1, "1")])
    return names


def percent(sorted_vals, p):
    if not sorted_vals:
        return 0.0
    k = max(0, min(len(sorted_vals) - 1, int(round(0.01 * p * len(sorted_vals))) - 1))
    return sorted_vals[k]


def run_scenario(name, base, path, method, body, token, concurrency, tasks, body_fn=None):
    url = base + path
    lat = []
    errors = 0

    def one(i):
        b = body_fn(i) if body_fn else body
        c, cost, txt = http(method, url, b, token)
        ok = (c == 200 and '"code":0' in txt.replace(" ", ""))
        return cost, ok

    wall0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        for cost, ok in pool.map(one, range(tasks)):
            lat.append(cost)
            if not ok:
                errors += 1
    wall = time.perf_counter() - wall0
    lat.sort()
    return {
        "scenario": name,
        "method": method,
        "path": path,
        "concurrency": concurrency,
        "tasks": tasks,
        "wall_seconds": round(wall, 3),
        "tps": round(tasks / wall, 2),
        "avg_ms": round(statistics.fmean(lat), 1),
        "p50_ms": round(percent(lat, 50), 1),
        "p90_ms": round(percent(lat, 90), 1),
        "p95_ms": round(percent(lat, 95), 1),
        "p99_ms": round(percent(lat, 99), 1),
        "min_ms": round(lat[0], 1),
        "max_ms": round(lat[-1], 1),
        "error_count": errors,
        "error_rate": round(errors / tasks * 100, 2),
        "latencies": [round(x, 1) for x in lat],
    }


SCENARIOS = {
    "login":      ("登录（含 BCrypt 校验）", "POST", "/auth/login",
                   {"username": "admin", "password": "123456"}),
    "plan_page":  ("培训计划分页查询", "GET", "/train/plan/page?pageNum=1&pageSize=10", None),
    "skill_gap":  ("技能差距计算（userId=5）", "GET", "/skill/gap/5", None),
    "course_page": ("课程分页查询", "GET", "/train/course/page?pageNum=1&pageSize=10", None),
    "user_page":  ("用户分页查询", "GET", "/system/user/page?pageNum=1&pageSize=10", None),
    "rag_search": ("知识库检索（BM25）", "GET",
                   "/ai/rag/search?q=" + urllib.parse.quote("需求分析"), None),
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:8000/api")
    ap.add_argument("--concurrency", type=int, default=50)
    ap.add_argument("--tasks", type=int, default=300)
    ap.add_argument("--scenario", default="all")
    ap.add_argument("--outdir", default="outputs")
    args = ap.parse_args()

    token = login(args.base)
    names = list(SCENARIOS) if args.scenario == "all" else [args.scenario]
    accounts = fetch_accounts(args.base, token) if "login" in names else []
    if accounts:
        print("[info] 登录场景使用账号池：%d 个账号轮询" % len(accounts))
    results = []
    for n in names:
        title, method, path, body = SCENARIOS[n]
        # Sa-Token 默认 max-login-count=12：同账号并发登录会踢掉最早会话，
        # 因此每个场景开始前重新登录，保证本次场景内 token 有效。
        if n != "login":
            token = login(args.base)
        print("[run] %-28s %s %s  并发=%d 请求=%d"
              % (title, method, path, args.concurrency, args.tasks))
        # 登录接口本身不携带 Authorization：携带已失效的旧 token 会被鉴权拦截器
        # 判为 401（code=401），与登录成功与否无关，属压测脚本自身的脏数据。
        tok = None if n == "login" else token
        body_fn = None
        if n == "login" and accounts:
            # 用账号池轮询，避免 Sa-Token max-login-count=12 造成的同账号互踢
            n_acc = len(accounts)
            body_fn = lambda i, _a=accounts, _n=n_acc: {
                "username": _a[i % _n], "password": "123456"}
        r = run_scenario(n, args.base, path, method, body, tok,
                         args.concurrency, args.tasks, body_fn)
        r["title"] = title
        results.append(r)
        print("      avg=%sms p95=%sms tps=%s err=%s%%"
              % (r["avg_ms"], r["p95_ms"], r["tps"], r["error_rate"]))

    os.makedirs(args.outdir, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    jpath = os.path.join(args.outdir, "loadtest-%s.json" % stamp)
    with open(jpath, "w", encoding="utf-8") as f:
        json.dump({"timestamp": stamp, "concurrency": args.concurrency,
                   "tasks": args.tasks, "results": results}, f, ensure_ascii=False, indent=2)

    lines = ["# 并发压测结果（%s）" % stamp, "",
             "并发数 %d，每场景 %d 次请求，直连本机 8000 端口。" % (args.concurrency, args.tasks), "",
             "| 场景 | 请求数 | 平均 | P50 | P90 | P95 | P99 | 最大 | TPS | 错误率 |",
             "|---|---|---|---|---|---|---|---|---|---|"]
    for r in results:
        lines.append("| %s | %d | %s ms | %s ms | %s ms | %s ms | %s ms | %s ms | %s | %s%% |"
                     % (r["title"], r["tasks"], r["avg_ms"], r["p50_ms"], r["p90_ms"],
                        r["p95_ms"], r["p99_ms"], r["max_ms"], r["tps"], r["error_rate"]))
    mpath = os.path.join(args.outdir, "loadtest-%s.md" % stamp)
    with open(mpath, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print("\n已落盘：%s\n已落盘：%s" % (jpath, mpath))


if __name__ == "__main__":
    main()
