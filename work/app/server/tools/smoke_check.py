import json
import urllib.request

BASE = "http://127.0.0.1:3000/api"


def call(path, token=None, method="GET", body=None):
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read().decode("utf-8"))


def login(u, p="123456"):
    d = call("/auth/login", method="POST", body={"username": u, "password": p})
    assert d.get("code") == 0, d
    return d["data"]["token"], d["data"].get("roles")


ok = 0
fail = 0


def check(name, cond, detail=""):
    global ok, fail
    if cond:
        ok += 1
        print("  [PASS] %-28s %s" % (name, detail))
    else:
        fail += 1
        print("  [FAIL] %-28s %s" % (name, detail))


print("=== 1. 登录（三种角色）===")
for u, role in [("admin", "ADMIN"), ("lecturer01", "LECTURER"), ("emp001", "EMPLOYEE")]:
    try:
        t, roles = login(u)
        check(u, t is not None and role in (roles or []), "roles=%s" % roles)
    except Exception as e:
        check(u, False, str(e))

admin_t, _ = login("admin")
lect_t, _ = login("lecturer01")
emp_t, _ = login("emp001")

print("\n=== 2. AI 模块状态 ===")
try:
    st = call("/ai/status", admin_t)["data"]
    check("AI 状态", True, "llmAvailable=%s mode=%s" % (st.get("llmAvailable"), st.get("mode")))
except Exception as e:
    check("AI 状态", False, str(e))

print("\n=== 3. 技能差距分析 ===")
try:
    g = call("/skill/gap/me", emp_t)["data"]
    items = g.get("items") or []
    check("员工差距", len(items) > 0,
          "达标率 %s%% / 差距 %s 项 / 加权差距 %s" % (g.get("matchRate"), len(items), g.get("totalWeightedGap")))
except Exception as e:
    check("员工差距", False, str(e))

try:
    h = call("/skill/gap/heatmap", admin_t)["data"]
    check("群体短板", isinstance(h, list) and len(h) > 0, "%s 条" % (len(h) if isinstance(h, list) else "?"))
except Exception as e:
    check("群体短板", False, str(e))

print("\n=== 4. 培训计划与进度 ===")
try:
    p = call("/train/plan/page?pageNum=1&pageSize=5", lect_t)["data"]
    recs = p.get("records") or []
    check("计划列表", True, "共 %s 条" % p.get("total"))
    if recs:
        pid = recs[0]["id"]
        d = call("/train/plan/%s" % pid, lect_t)["data"]
        plan = d.get("plan") or {}
        check("计划详情", plan.get("id") == pid, "状态=%s / 课程=%s 门" % (plan.get("status"), plan.get("courseCount")))
except Exception as e:
    check("计划列表", False, str(e))

try:
    db = call("/train/progress/dashboard", lect_t)["data"]
    check("进度看板", True,
          "计划 %s / 完成率 %s%% / 学时 %s" % (db.get("planTotal"), db.get("overallFinishRate"), db.get("learnHours")))
except Exception as e:
    check("进度看板", False, str(e))

print("\n=== 5. 考核模块 ===")
try:
    q = call("/exam/question/page?pageNum=1&pageSize=3", lect_t)["data"]
    check("题库", True, "共 %s 题" % q.get("total"))
except Exception as e:
    check("题库", False, str(e))

try:
    pa = call("/exam/paper/page?pageNum=1&pageSize=5", lect_t)["data"]
    check("试卷列表", True, "共 %s 份" % pa.get("total"))
except Exception as e:
    check("试卷列表", False, str(e))

print("\n=== 6. RAG 知识库 ===")
try:
    r = call("/ai/rag/search?q=%E5%AE%89%E5%85%A8%E7%94%9F%E4%BA%A7&topK=3", admin_t)["data"]
    check("RAG 检索", isinstance(r, list), "命中 %s 条" % (len(r) if isinstance(r, list) else "?"))
except Exception as e:
    check("RAG 检索", False, str(e))

print("\n=== 7. 组织与人员 ===")
try:
    u = call("/system/user/page?pageNum=1&pageSize=5", admin_t)["data"]
    check("用户分页", True, "共 %s 人" % u.get("total"))
except Exception as e:
    check("用户分页", False, str(e))

print("\n" + "=" * 46)
print("冒烟测试：通过 %d 项，失败 %d 项" % (ok, fail))
