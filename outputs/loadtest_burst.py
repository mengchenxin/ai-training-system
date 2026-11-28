# -*- coding: utf-8 -*-
"""突发与登录测量：各重复 3 轮取中位数。"""
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


def conn():
    return http.client.HTTPConnection(HOST, PORT, timeout=60)


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


def burst(n, token, path):
    lat = [0.0] * n
    ok = [0] * n
    b = threading.Barrier(n)

    def w(i):
        c = conn()
        try:
            b.wait(timeout=180)
            t0 = time.perf_counter()
            c.request("GET", path, headers={"Authorization": "Bearer " + token})
            r = c.getresponse()
            pl = r.read()
            lat[i] = (time.perf_counter() - t0) * 1000
            ok[i] = 1 if pl.startswith(OK) else 0
        except Exception:
            lat[i] = -1
        finally:
            try:
                c.close()
            except Exception:
                pass

    ts = [threading.Thread(target=w, args=(i,)) for i in range(n)]
    t0 = time.time()
    for t in ts:
        t.start()
    for t in ts:
        t.join()
    el = time.time() - t0
    good = [x for x in lat if x >= 0]
    return {"threads": n, "ok": sum(ok), "wall_s": round(el, 2),
            "avg_ms": round(statistics.mean(good), 1),
            "p95_ms": round(pct(good, 95), 1), "max_ms": round(max(good), 1)}


def login_serial(n=10):
    lat = []
    for _ in range(n):
        c = conn()
        body = json.dumps({"username": "admin", "password": PW}).encode()
        t0 = time.perf_counter()
        c.request("POST", BASE + "/auth/login", body=body,
                  headers={"Content-Type": "application/json"})
        r = c.getresponse()
        r.read()
        lat.append((time.perf_counter() - t0) * 1000)
        c.close()
    return {"n": n, "median_ms": round(statistics.median(lat), 1),
            "p95_ms": round(pct(lat, 95), 1), "max_ms": round(max(lat), 1)}


def main():
    out = {}
    tok = login()
    # 预热
    for _ in range(3):
        burst(20, tok, BASE + "/train/plan/page?current=1&size=10")

    for n in (50, 100, 200):
        runs = [burst(n, tok, BASE + "/train/plan/page?current=1&size=10") for _ in range(3)]
        out["burst_%d" % n] = {
            "threads": n,
            "ok": min(r["ok"] for r in runs),
            "wall_s": round(statistics.median([r["wall_s"] for r in runs]), 2),
            "avg_ms": round(statistics.median([r["avg_ms"] for r in runs]), 1),
            "p95_ms": round(statistics.median([r["p95_ms"] for r in runs]), 1),
            "max_ms": round(statistics.median([r["max_ms"] for r in runs]), 1),
        }
        print(json.dumps(out["burst_%d" % n], ensure_ascii=False), flush=True)

    out["login_serial"] = login_serial(10)
    print(json.dumps(out["login_serial"], ensure_ascii=False), flush=True)

    json.dump(out, io.open("D:/Desktop/learn/graduate/bi/outputs/loadtest-burst.json", "w"),
              ensure_ascii=False, indent=2)
    print("SAVED")


main()
