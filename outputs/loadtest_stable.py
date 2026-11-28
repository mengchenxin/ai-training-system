# -*- coding: utf-8 -*-
"""稳定性测量：串行基线 + 重复并发取中位数。"""
import http.client
import io
import json
import statistics
import threading
import time

HOST, PORT, BASE = "127.0.0.1", 8000, "/api"
OK = b'{"code":0'
PW = [l.split(":", 1)[1].strip() for l in io.open(
    "D:/Desktop/learn/graduate/bi/work/app/server/application-local.yml", encoding="utf-8")
    if "password:" in l][0]

EPS = [
    ("课程分页", BASE + "/train/course/page?current=1&size=10"),
    ("计划分页", BASE + "/train/plan/page?current=1&size=10"),
    ("需求分页", BASE + "/train/need/page?current=1&size=10"),
    ("题库分页", BASE + "/exam/question/page?current=1&size=10"),
    ("用户分页", BASE + "/system/user/page?current=1&size=10"),
    ("技能列表", BASE + "/skill/list"),
    ("计划详情", BASE + "/train/plan/2"),
    ("技能热力图", BASE + "/skill/gap/heatmap"),
]


def conn():
    return http.client.HTTPConnection(HOST, PORT, timeout=60)


def call(c, m, p, body=None, token=None):
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = "Bearer " + token
    d = json.dumps(body).encode() if body is not None else None
    t0 = time.perf_counter()
    c.request(m, p, body=d, headers=h)
    r = c.getresponse()
    pl = r.read()
    return (time.perf_counter() - t0) * 1000, pl


def login():
    c = conn()
    c.request("POST", BASE + "/auth/login",
              body=json.dumps({"username": "admin", "password": PW}).encode(),
              headers={"Content-Type": "application/json"})
    d = json.loads(c.getresponse().read().decode())["data"]
    c.close()
    return d["token"]


def pct(v, p):
    s = sorted(v)
    return s[int(round((p / 100.0) * (len(s) - 1)))]


def serial(name, path, token, n=30):
    c = conn()
    lat = []
    bad = 0
    for _ in range(n):
        dt, pl = call(c, "GET", path, None, token)
        lat.append(dt)
        if not pl.startswith(OK):
            bad += 1
    c.close()
    return {"name": name, "n": n, "fail": bad,
            "median_ms": round(statistics.median(lat), 1),
            "p95_ms": round(pct(lat, 95), 1), "max_ms": round(max(lat), 1)}


def conc(name, c_level, dur, token, eps):
    lat, ok, biz, net = [], [0], [0], [0]
    lock = threading.Lock()
    stop = time.time() + dur
    b = threading.Barrier(c_level)

    def w(i):
        c = conn()
        ll, o, bz, ne = [], 0, 0, 0
        try:
            b.wait(timeout=60)
            k = i
            while time.time() < stop:
                _, p = eps[k % len(eps)]
                try:
                    dt, pl = call(c, "GET", p, None, token)
                    ll.append(dt)
                    if pl.startswith(OK):
                        o += 1
                    else:
                        bz += 1
                except Exception:
                    ne += 1
                    try:
                        c.close()
                    except Exception:
                        pass
                    c = conn()
                k += 1
        finally:
            try:
                c.close()
            except Exception:
                pass
            with lock:
                lat.extend(ll)
                ok[0] += o
                biz[0] += bz
                net[0] += ne

    ts = [threading.Thread(target=w, args=(i,)) for i in range(c_level)]
    t0 = time.time()
    for t in ts:
        t.start()
    for t in ts:
        t.join()
    el = time.time() - t0
    total = len(lat) + net[0]
    return {"name": name, "concurrency": c_level, "requests": total,
            "success_pct": round(100.0 * ok[0] / total, 2) if total else 0,
            "tps": round(ok[0] / el, 1),
            "median_ms": round(statistics.median(lat), 1) if lat else 0,
            "p95_ms": round(pct(lat, 95), 1), "p99_ms": round(pct(lat, 99), 1)}


def main():
    token = login()
    c = conn()
    for _ in range(10):
        for _, p in EPS:
            call(c, "GET", p, None, token)
    c.close()

    out = {"serial": [], "concurrent": []}
    for nm, p in EPS:
        out["serial"].append(serial(nm, p, token))
    print(json.dumps(out["serial"], ensure_ascii=False, indent=1), flush=True)

    light = EPS[:7]
    for conc_level in (10, 30):
        runs = []
        for _ in range(3):
            runs.append(conc("轻量读", conc_level, 12, token, light))
        tps = [r["tps"] for r in runs]
        p95 = [r["p95_ms"] for r in runs]
        med = median_run(runs)
        med["runs_tps"] = tps
        med["runs_p95"] = p95
        out["concurrent"].append(med)
        print(json.dumps(med, ensure_ascii=False), flush=True)

    json.dump(out, io.open("D:/Desktop/learn/graduate/bi/outputs/loadtest-stable.json", "w"),
              ensure_ascii=False, indent=2)
    print("SAVED")


def median_run(runs):
    return {"name": runs[0]["name"], "concurrency": runs[0]["concurrency"],
            "requests": int(statistics.median([r["requests"] for r in runs])),
            "success_pct": min(r["success_pct"] for r in runs),
            "tps": round(statistics.median([r["tps"] for r in runs]), 1),
            "median_ms": round(statistics.median([r["median_ms"] for r in runs]), 1),
            "p95_ms": round(statistics.median([r["p95_ms"] for r in runs]), 1),
            "p99_ms": round(statistics.median([r["p99_ms"] for r in runs]), 1)}


main()
