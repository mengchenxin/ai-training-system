#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
AI 方案生成接口的并发测试（与纯接口压测分开，因涉及外部大模型调用与费用）。

用法：
    python ai_load_test.py --mode RULE     --concurrency 10 --tasks 20
    python ai_load_test.py --mode HYBRID   --concurrency 5  --tasks 8

说明：每次请求带 refresh=true 以绕过 30 分钟缓存，测的是真实生成耗时。
输出：outputs/ai-loadtest-<mode>-<时间戳>.json / .md
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

OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def http(method, url, body=None, token=None, timeout=180):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    t0 = time.perf_counter()
    try:
        with OPENER.open(req, timeout=timeout) as resp:
            return resp.status, (time.perf_counter() - t0) * 1000.0, resp.read()[:400].decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, (time.perf_counter() - t0) * 1000.0, e.read()[:400].decode("utf-8", "replace")
    except Exception as e:
        return -1, (time.perf_counter() - t0) * 1000.0, type(e).__name__ + ": " + str(e)[:150]


def pct(vals, p):
    if not vals:
        return 0.0
    k = max(0, min(len(vals) - 1, int(round(0.01 * p * len(vals))) - 1))
    return vals[k]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:8000/api")
    ap.add_argument("--mode", default="HYBRID")
    ap.add_argument("--concurrency", type=int, default=5)
    ap.add_argument("--tasks", type=int, default=8)
    ap.add_argument("--budget", default="40")
    ap.add_argument("--user-start", type=int, default=1)
    ap.add_argument("--outdir", default="outputs")
    args = ap.parse_args()

    code, _, body = http("POST", args.base + "/auth/login",
                         {"username": "admin", "password": "123456"})
    if code != 200:
        sys.exit("登录失败：" + body)
    token = json.loads(body)["data"]["token"]

    def one(i):
        uid = args.user_start + (i % 10)
        q = urllib.parse.urlencode({"userId": uid, "budget": args.budget,
                                    "mode": args.mode, "refresh": "true"})
        c, cost, txt = http("POST", args.base + "/ai/plan/generate?" + q, None, token)
        ok = (c == 200 and '"code":0' in txt.replace(" ", ""))
        return cost, ok, txt[:150]

    print("[run] mode=%s 并发=%d 请求=%d（refresh=true，绕过缓存）"
          % (args.mode, args.concurrency, args.tasks))
    wall0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        rs = list(pool.map(one, range(args.tasks)))
    wall = time.perf_counter() - wall0

    lat = sorted(r[0] for r in rs)
    errs = [r[2] for r in rs if not r[1]]
    result = {
        "mode": args.mode,
        "concurrency": args.concurrency,
        "tasks": args.tasks,
        "wall_seconds": round(wall, 2),
        "tps": round(args.tasks / wall, 2),
        "avg_ms": round(statistics.fmean(lat), 1),
        "p50_ms": round(pct(lat, 50), 1),
        "p95_ms": round(pct(lat, 95), 1),
        "max_ms": round(lat[-1], 1),
        "min_ms": round(lat[0], 1),
        "error_count": len(errs),
        "error_rate": round(len(errs) / args.tasks * 100, 2),
        "error_samples": errs[:3],
        "latencies": [round(x, 1) for x in lat],
    }
    os.makedirs(args.outdir, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    jp = os.path.join(args.outdir, "ai-loadtest-%s-%s.json" % (args.mode, stamp))
    with open(jp, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print("      avg=%sms p50=%sms p95=%sms max=%sms tps=%s err=%s%%"
          % (result["avg_ms"], result["p50_ms"], result["p95_ms"],
             result["max_ms"], result["tps"], result["error_rate"]))
    if errs:
        print("      失败样本:", errs[0])
    print("已落盘：" + jp)


if __name__ == "__main__":
    main()
