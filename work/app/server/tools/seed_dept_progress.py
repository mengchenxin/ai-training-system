# -*- coding: utf-8 -*-
"""通过业务接口为各部门补建培训计划并上报进度，让进度看板的部门完成率有数据。"""
import json
import random
import urllib.request

BASE = "http://127.0.0.1:8000/api"
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with opener.open(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def login(username, password="123456"):
    r = call("POST", "/auth/login", {"username": username, "password": password})
    if r.get("code") != 0:
        raise RuntimeError("login fail %s: %s" % (username, r.get("msg")))
    return r["data"]["token"]


HOURS = {1: 4.0, 2: 10.0, 3: 6.0, 4: 8.0, 5: 16.0, 6: 8.0, 7: 8.0, 8: 10.0,
         9: 4.0, 10: 6.0, 11: 16.0, 12: 6.0, 13: 4.0, 14: 10.0, 15: 6.0,
         16: 8.0, 17: 16.0, 18: 6.0, 19: 8.0, 20: 16.0, 21: 4.0, 22: 6.0,
         23: 12.0, 24: 6.0, 25: 8.0, 26: 16.0, 27: 8.0, 28: 6.0, 29: 10.0,
         30: 4.0}

# (部门ID, 部门名, 课程ID列表, 目标平均完成率)
DEPTS = [
    (1, "生产部", [25, 30, 28], 60),
    (2, "质量管理部", [12, 13, 15], 76),
    (3, "设备动力部", [9, 10, 18], 38),
    (4, "技术支持部", [16, 19, 24], 88),
    (5, "人力资源部", [22, 21, 27], 22),
]

admin = login("admin")
users = call("GET", "/system/user/page?current=1&size=200", token=admin)["data"]["records"]
by_dept = {}
for u in users:
    by_dept.setdefault(u.get("deptId"), []).append(u)

for dept_id, dept_name, course_ids, target in DEPTS:
    courses = [{"courseId": cid, "seqNo": i + 1, "expectHours": HOURS[cid]}
               for i, cid in enumerate(course_ids)]
    form = {
        "planName": dept_name + "岗位能力提升计划",
        "planType": "DEPT",
        "targetDeptId": dept_id,
        "startDate": "2026-09-01",
        "endDate": "2026-12-31",
        "genType": "MANUAL",
        "courses": courses,
    }
    r = call("POST", "/train/plan", form, admin)
    if r.get("code") != 0:
        print("[失败] 建计划 %s -> %s" % (dept_name, r.get("msg")))
        continue
    pid = r["data"]

    for st in ("PENDING", "PUBLISHED", "RUNNING"):
        rr = call("POST", "/train/plan/%d/status?status=%s" % (pid, st), None, admin)
        if rr.get("code") != 0:
            print("   ! 状态流转 %s 失败: %s" % (st, rr.get("msg")))
    print("[计划] %s id=%s 课程%d 目标%d%%" % (dept_name, pid, len(courses), target))

    for u in by_dept.get(dept_id, []):
        try:
            tk = login(u["username"])
        except Exception as e:
            print("   - 跳过 %s: %s" % (u["username"], e))
            continue
        mine = call("GET", "/train/progress/my", token=tk)["data"]
        todo = [p for p in mine if p["planId"] == pid]
        if not todo:
            print("   - %s 无进度记录" % u["username"])
            continue
        pct = max(0, min(100, target + random.randint(-8, 8)))
        ok = 0
        for p in todo:
            hrs = round(HOURS[p["courseId"]] * pct / 100.0, 1)
            rp = call("POST", "/train/progress/report",
                      {"id": p["id"], "progressPercent": pct,
                       "spentHours": hrs, "remark": "按期完成阶段学习"}, tk)
            if rp.get("code") == 0:
                ok += 1
            else:
                print("   ! 上报失败 %s#%s: %s" % (u["username"], p["id"], rp.get("msg")))
        print("   + %s(%s) -> %d%%  %d/%d 条"
              % (u.get("realName"), u["username"], pct, ok, len(todo)))

print("DONE")
