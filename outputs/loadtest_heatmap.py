# -*- coding: utf-8 -*-
"""热力图并发对比：10 并发 × 3 轮取中位数。"""
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


def run(n, per, token):
    lat, ok = [], [0]
    lock = threading.Lock()
    b = threading.Barrier(n)

    def w():
        c = conn()
        ll, o = [], 0
        try:
            b.wait(timeout=60)
            for _ in range(per):
                t0 = time.perf_counter()
                c.request("GET", BASE + "/skill/gap/heatmap",
                          headers={"Authorization": "Bearer " + token})
                r = c.getresponse()
                pl = r.read()
                ll.append((time.perf_counter() - t0) * 1000)
                if pl.startswith(OK):
                    o += 1
        finally:
            try:
                c.close()
            except Exception:
                pass
            with lock:
                lat.extend(ll)
                ok[0] += o

    ts = [threading.Thread(target=w) for _ in range(n)]
    t0 = time.time()
    for t in ts:
        t.start()
    for t in ts:
        t.join()
    el = time.time() - t0
    return {"requests": len(lat), "ok": ok[0], "tps": round(ok[0] / el, 1),
            "avg_ms": round(statistics.mean(lat), 1),
            "p95_ms": round(pct(lat, 95), 1)}


def main():
    token = login()
    for _ in range(2):
        run(1, 3, token)
    out = {}
    for n, per in ((1, 20), (5, 10), (10, 6)):
        runs = [run(n, per, token) for _ in range(3)]
        r = {"concurrency": n, "requests": runs[0]["requests"],
             "ok": min(x["ok"] for x in runs),
             "tps": round(statistics.median([x["tps"] for x in runs]), 1),
             "avg_ms": round(statistics.median([x["avg_ms"] for x in runs]), 1),
             "p95_ms": round(statistics.median([x["p95_ms"] for x in runs]), 1)}
        out["c%d" % n] = r
        print(json.dumps(r, ensure_ascii=False), flush=True)
    json.dump(out, io.open("D:/Desktop/learn/graduate/bi/outputs/loadtest-heatmap.json", "w"),
              ensure_ascii=False, indent=2)
    print("SAVED")


main()
