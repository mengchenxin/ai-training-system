#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
三组对比实验执行脚本（规则 / 混合 / 纯大模型）。

用途：一次性跑完开题要求的「三组对比」，把每次调用的指标落成 CSV，
      供论文实验章节直接取数，避免手工点页面 60 次还容易漏样本。

用法：
    python run_experiment.py --samples 20 --budget 40
    python run_experiment.py --samples 5 --base http://127.0.0.1:8000/api

参数：
    --samples 参与实验的员工数（默认 20）
    --budget  学时预算（默认 40）
    --out     输出 CSV 路径（默认 outputs/实验数据-三组对比-<时间>.csv）

说明：
    * 每次调用都带 refresh=true，绕过结果缓存，保证是真实调用；
    * 纯规则组不消耗 token，成本列留空；
    * 幻觉课程数只有纯大模型组非零，这是「约束必要性」的量化证据。
"""
import argparse
import csv
import json
import os
import sys
import time
import urllib.parse
import urllib.request

MODES = ["RULE", "HYBRID", "LLM_ONLY"]


def make_opener():
    # 本机调用，必须绕过系统代理，否则 127.0.0.1 会被代理劫持
    return urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(opener, url, method="GET", body=None, token=None, timeout=180):
    data = None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with opener.open(req, timeout=timeout) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    if payload.get("code") != 0:
        raise RuntimeError(f"{url} -> {payload}")
    return payload.get("data")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:8000/api")
    ap.add_argument("--user", default="admin")
    ap.add_argument("--password", default="123456")
    ap.add_argument("--samples", type=int, default=20)
    ap.add_argument("--budget", type=int, default=40)
    ap.add_argument("--out", default="")
    args = ap.parse_args()

    opener = make_opener()
    token = call(opener, args.base + "/auth/login", "POST",
                 {"username": args.user, "password": args.password})["token"]

    page = call(opener, args.base + "/system/user/page?current=1&size=500&status=1", token=token)
    employees = [u for u in page["records"] if "EMPLOYEE" in (u.get("roles") or [])]
    if not employees:
        print("没有找到员工账号，无法做实验")
        return 1
    picked = employees[: args.samples]
    print(f"参与实验员工 {len(picked)} 人，模式 {MODES}，预算 {args.budget} 学时")

    rows = []
    total = len(picked) * len(MODES)
    done = 0
    for u in picked:
        for mode in MODES:
            done += 1
            q = urllib.parse.urlencode({
                "userId": u["id"], "budget": args.budget, "mode": mode, "refresh": "true"
            })
            t0 = time.time()
            try:
                d = call(opener, f"{args.base}/ai/plan/generate?{q}", "POST", token=token)
                courses = d.get("courses") or []
                rows.append({
                    "userId": u["id"],
                    "realName": u.get("realName") or "",
                    "postName": u.get("postName") or "",
                    "mode": mode,
                    "genType": d.get("genType"),
                    "courseCount": len(courses),
                    "totalHours": d.get("totalHours"),
                    "budget": d.get("budget"),
                    "overBudget": 1 if (d.get("budget") and float(d.get("totalHours") or 0) > float(d.get("budget"))) else 0,
                    "courseNames": " | ".join(c.get("courseName", "") for c in courses),
                    "promptTokens": d.get("promptTokens") or 0,
                    "completionTokens": d.get("completionTokens") or 0,
                    "costMs": d.get("costMs"),
                    "hallucination": d.get("hallucinationCount") or 0,
                    "schemaValid": 1 if d.get("schemaValid") else 0,
                    "fallback": d.get("fallbackReason") or "",
                    "recordId": d.get("aiRecordId"),
                })
                flag = "!" if d.get("fallbackReason") else ""
                print(f"[{done}/{total}] {u.get('realName')} {mode:9s} 课{len(courses)}门 "
                      f"{d.get('totalHours')}h {d.get('costMs')}ms 幻觉{d.get('hallucinationCount') or 0}{flag}")
            except Exception as e:  # 单条失败不中断整组实验
                rows.append({
                    "userId": u["id"], "realName": u.get("realName") or "",
                    "postName": u.get("postName") or "", "mode": mode,
                    "genType": "ERROR", "courseCount": 0, "totalHours": 0, "budget": args.budget,
                    "overBudget": 0, "courseNames": "", "promptTokens": 0, "completionTokens": 0,
                    "costMs": int((time.time() - t0) * 1000), "hallucination": 0,
                    "schemaValid": 0, "fallback": str(e)[:180], "recordId": ""
                })
                print(f"[{done}/{total}] {u.get('realName')} {mode:9s} 失败：{e}")

    out = args.out or os.path.join(
        "outputs", "实验数据-三组对比-" + time.strftime("%Y%m%d-%H%M") + ".csv")
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    with open(out, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)
    print("\n已写入：" + out)

    # 控制台汇总，直接抄进论文表格
    print("\n模式        样本  平均课程数  平均学时  超预算率  平均耗时(ms)  平均tokens  平均幻觉课程  成功率")
    for m in MODES:
        s = [r for r in rows if r["mode"] == m]
        if not s:
            continue
        n = len(s)
        ok = sum(1 for r in s if r["genType"] != "ERROR" and not r["fallback"])
        print(f"{m:11s} {n:4d}  {sum(r['courseCount'] for r in s)/n:9.2f}  "
              f"{sum(float(r['totalHours'] or 0) for r in s)/n:8.2f}  "
              f"{sum(r['overBudget'] for r in s)/n*100:7.1f}%  "
              f"{sum(int(r['costMs'] or 0) for r in s)/n:11.0f}  "
              f"{sum(int(r['promptTokens'])+int(r['completionTokens']) for r in s)/n:10.0f}  "
              f"{sum(int(r['hallucination']) for r in s)/n:12.2f}  {ok/n*100:5.1f}%")
    return 0


if __name__ == "__main__":
    sys.exit(main())
