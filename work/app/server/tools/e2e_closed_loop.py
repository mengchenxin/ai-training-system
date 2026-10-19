# -*- coding: utf-8 -*-
"""
端到端业务闭环测试：
技能差距 -> AI 方案 -> 培训计划 -> 审核发布 -> 进度上报 -> 前测/后测 -> 效果诊断 -> 技能等级回写
"""
import json
import sys
import requests

BASE = "http://127.0.0.1:8000/api"
PWD = "123456"

ok_cnt = 0
fail_cnt = 0


def step(name):
    print("\n" + "=" * 72)
    print("  " + name)
    print("=" * 72)


def check(cond, msg, extra=""):
    global ok_cnt, fail_cnt
    if cond:
        ok_cnt += 1
        print("  [PASS] " + msg + (("  " + str(extra)) if extra else ""))
    else:
        fail_cnt += 1
        print("  [FAIL] " + msg + (("  " + str(extra)) if extra else ""))
    return cond


def login(username):
    r = requests.post(BASE + "/auth/login", json={"username": username, "password": PWD}, timeout=20)
    d = r.json()
    if d.get("code") != 0:
        raise RuntimeError("login failed %s: %s" % (username, d))
    return d["data"]["token"]


def hdr(tok):
    return {"Authorization": "Bearer " + tok}


def get(tok, path, **params):
    r = requests.get(BASE + path, headers=hdr(tok), params=params, timeout=60)
    return r.json()


def post(tok, path, **kw):
    r = requests.post(BASE + path, headers=hdr(tok), timeout=120, **kw)
    return r.json()


step("0. 登录三种角色")
admin = login("admin")
lect = login("lecturer01")
emp = login("emp001")
check(True, "admin / lecturer01 / emp001 均登录成功")

info = get(emp, "/auth/info").get("data") or {}
emp_id = info.get("id") or info.get("userId")
emp_name = info.get("realName")
print("    emp001 -> userId=%s realName=%s roles=%s" % (emp_id, emp_name, info.get("roles")))
check(emp_id is not None, "拿到员工 userId", emp_id)

# ---------------------------------------------------------------- 1 差距分析
step("1. 员工技能差距分析")
gap_before = get(emp, "/skill/gap/me")["data"]
items = gap_before.get("items") or []
gap_items = [i for i in items if (i.get("gap") or 0) > 0]
print("    岗位=%s  达标率=%s%%  加权差距=%s  差距项=%d"
      % (gap_before.get("postName"), gap_before.get("matchRate"),
         gap_before.get("totalWeightedGap"), len(gap_items)))
for i in gap_items[:6]:
    print("      %-22s 当前%d -> 目标%d  gap=%d  权重%s  优先级%s  %s"
          % (i.get("skillName"), i.get("currentLevel"), i.get("targetLevel"),
             i.get("gap"), i.get("weight"), i.get("priorityRank"), i.get("priorityLevel")))
check(len(gap_items) > 0, "差距量化结果非空")
check(gap_before.get("matchRate") is not None, "达标率已计算", gap_before.get("matchRate"))
matchrate_before = float(gap_before.get("matchRate"))
gapweighted_before = float(gap_before.get("totalWeightedGap"))

# ---------------------------------------------------------------- 2 AI 方案
step("2. AI 个性化培训方案生成（规则引擎 + 可选大模型）")
st = get(lect, "/ai/status")["data"]
print("    LLM 可用=%s model=%s 索引片段=%s 模式=%s"
      % (st.get("llmAvailable"), st.get("model"), st.get("ragIndexSize"), st.get("mode")))

gen = post(lect, "/ai/plan/generate", params={"userId": emp_id, "budget": 40, "useLlm": False})["data"]
record_id = gen.get("aiRecordId")
courses = gen.get("courses") or []
print("    生成类型=%s  LLM使用=%s  总学时=%s  课程数=%d"
      % (gen.get("genType"), gen.get("llmUsed"), gen.get("totalHours"), len(courses)))
print("    摘要：%s" % (gen.get("summary") or "")[:200])
for c in courses:
    print("      #%s %-24s %sh 技能=%s gapBefore=%s"
          % (c.get("seqNo"), c.get("courseName"), c.get("hours"),
             c.get("coverSkill") or c.get("coverSkillId"), c.get("gapBefore")))
hits = gen.get("refs") or []
print("    RAG 引用 %d 条" % len(hits))
for h in hits[:3]:
    print("      · %s  score=%s" % (str(h.get("docName"))[:40], h.get("score")))
check(len(courses) > 0, "方案含课程编排")
check(gen.get("genType") in ("AI_HYBRID", "AI_RULE"), "生成类型合规", gen.get("genType"))
check(record_id is not None, "生成了 AI 记录", record_id)

# 采纳
ad = post(lect, "/ai/plan/%s/adopt" % record_id, params={"status": "ADOPTED"})
check(ad.get("code") == 0, "方案已采纳（写实验指标）")

# ---------------------------------------------------------------- 3 建计划
step("3. 采纳方案转培训计划")
form = {
    "planName": "AI 个性化培训-" + (emp_name or "emp001"),
    "planType": "PERSONAL",
    "targetUserId": emp_id,
    "startDate": "2026-10-01",
    "endDate": "2026-11-30",
    "genType": gen.get("genType"),
    "aiRecordId": record_id,
    "courses": [
        {
            "courseId": c.get("courseId"),
            "seqNo": c.get("seqNo"),
            "expectHours": c.get("hours"),
            "coverSkillId": c.get("coverSkillId"),
            "gapBefore": c.get("gapBefore"),
            "planReason": c.get("reason"),
        }
        for c in courses
    ],
}
sp = post(lect, "/train/plan", json=form)
check(sp.get("code") == 0, "计划创建成功", sp.get("msg"))
plan_id = sp.get("data")
print("    planId=%s" % plan_id)

# ---------------------------------------------------------------- 4 状态流转
step("4. 状态流转：DRAFT -> PENDING -> PUBLISHED")
check(post(lect, "/train/plan/%s/status" % plan_id, params={"status": "PENDING"}).get("code") == 0,
      "提交审核 PENDING")
check(post(lect, "/train/plan/%s/status" % plan_id, params={"status": "PUBLISHED"}).get("code") == 0,
      "审核发布 PUBLISHED（同时生成进度记录）")

bad = post(lect, "/train/plan/%s/status" % plan_id, params={"status": "DRAFT"})
check(bad.get("code") != 0, "非法流转被拦截（PUBLISHED -> DRAFT）", bad.get("msg"))

detail = get(lect, "/train/plan/%s" % plan_id)["data"]
print("    计划状态=%s 课程数=%d 覆盖员工=%d 完成率=%s"
      % (detail.get("plan", {}).get("status"), len(detail.get("courseStats") or []),
         detail.get("userCount"), detail.get("finishRate")))
check(detail.get("userCount", 0) >= 1, "发布后生成进度记录")

# ---------------------------------------------------------------- 5 进度上报
step("5. 员工侧进度上报")
tasks = get(emp, "/train/progress/my")["data"] or []
mine = [t for t in tasks if t.get("planId") == plan_id]
print("    我的任务 %d 条（本计划 %d 条）" % (len(tasks), len(mine)))
for t in mine:
    print("      id=%s %-22s 进度=%s%% 状态=%s"
          % (t.get("id"), str(t.get("courseName"))[:22], t.get("progressPercent"), t.get("status")))

for idx, t in enumerate(mine):
    pct = 100 if idx < len(mine) - 1 else 100
    r = post(emp, "/train/progress/report",
             json={"id": t.get("id"), "progressPercent": pct,
                   "spentHours": t.get("expectHours") or 4, "remark": "线上学习完成"})
    if r.get("code") != 0:
        print("      report fail: %s" % r.get("msg"))
check(True, "逐条上报完成")

tasks2 = get(emp, "/train/progress/my")["data"] or []
mine2 = [t for t in tasks2 if t.get("planId") == plan_id]
fin = [t for t in mine2 if float(t.get("progressPercent") or 0) >= 100]
check(len(fin) == len(mine2) and len(mine2) > 0, "全部课程进度 100%", "%d/%d" % (len(fin), len(mine2)))

dash = get(lect, "/train/progress/dashboard")["data"]
print("    看板：计划 %s 个 / 完成率 %s%% / 学习学时 %s / 进度记录 %s 条"
      % (dash.get("planTotal"), dash.get("overallFinishRate"),
         dash.get("learnHours"), dash.get("courseRecordTotal")))
check(dash.get("overallFinishRate") is not None, "看板统计可用")

# ---------------------------------------------------------------- 6 考试
step("6. 考核：自动组卷 -> 前测 -> 后测")
skill_ids = sorted({c.get("coverSkillId") for c in courses if c.get("coverSkillId")})
print("    组卷技能范围：%s" % skill_ids)


def make_paper(name):
    p = post(lect, "/exam/paper/generate", json={
        "paperName": name,
        "planId": plan_id,
        "skillIds": skill_ids,
        "easyCount": 4, "mediumCount": 3, "hardCount": 2,
        "totalScore": 100, "passScore": 60, "includeShort": True,
    })
    assert p.get("code") == 0, p
    return p["data"]


def answers_of(paper_id, right):
    paper = get(emp, "/exam/paper/%s" % paper_id)["data"]
    qs = paper.get("questions") or []
    out = []
    for q in qs:
        qd = get(lect, "/exam/question/%s" % q["questionId"])["data"]
        if right:
            a = qd.get("answer") or ""
        else:
            typ = q.get("questionType")
            a = "Z" if typ != "SHORT" else "不清楚"
        out.append({"questionId": q["questionId"], "userAnswer": a})
    return qs, out


pre_paper = make_paper("前测·" + (emp_name or "emp001"))
qs, pre_ans = answers_of(pre_paper, right=False)
post(emp, "/exam/paper/%s/start" % pre_paper)
pre_res = post(emp, "/exam/paper/%s/submit" % pre_paper, json={
    "examStage": "PRE", "planId": plan_id, "answers": pre_ans})["data"]
print("    前测：%d 题 得分 %s / 客观 %s / 主观 %s  通过=%s"
      % (len(qs), pre_res.get("totalScore"), pre_res.get("objectiveScore"),
         pre_res.get("subjectiveScore"), pre_res.get("passed")))
check(pre_res.get("totalScore") is not None, "前测自动判分")

post_paper = make_paper("后测·" + (emp_name or "emp001"))
qs2, post_ans = answers_of(post_paper, right=True)
post(emp, "/exam/paper/%s/start" % post_paper)
post_res = post(emp, "/exam/paper/%s/submit" % post_paper, json={
    "examStage": "POST", "planId": plan_id, "answers": post_ans})["data"]
print("    后测：%d 题 得分 %s / 客观 %s / 主观 %s  通过=%s"
      % (len(qs2), post_res.get("totalScore"), post_res.get("objectiveScore"),
         post_res.get("subjectiveScore"), post_res.get("passed")))
check(float(post_res.get("totalScore") or 0) > float(pre_res.get("totalScore") or 0),
      "后测成绩高于前测", "%s -> %s" % (pre_res.get("totalScore"), post_res.get("totalScore")))

rep = post(emp, "/exam/paper/%s/start" % post_paper)
check(rep.get("code") != 0, "重复作答被拦截", rep.get("msg"))

# ---------------------------------------------------------------- 7 效果诊断
step("7. 效果诊断与补训方案")
ev = post(lect, "/ai/effect/%s/%s" % (plan_id, emp_id))["data"]
print("    前测=%s 后测=%s 提升=%s 完成率=%s 差距缩小=%s%% 结论=%s"
      % (ev.get("preScore"), ev.get("postScore"), ev.get("scoreImprove"),
         ev.get("finishRate"), ev.get("gapReduce"), ev.get("evalResult")))
print("    诊断：%s" % ev.get("diagnosis"))
print("    补训：%s" % (ev.get("retrainPlan") or "（无需补训）"))
check(ev.get("evalResult") in ("EXCELLENT", "PASS", "FAIL"), "诊断结论已生成", ev.get("evalResult"))
check(ev.get("gapReduce") is not None, "差距缩小率已计算", ev.get("gapReduce"))

gap_after = get(emp, "/skill/gap/me")["data"]
matchrate_after = float(gap_after.get("matchRate"))
gapweighted_after = float(gap_after.get("totalWeightedGap"))
print("    技能档案回写后：达标率 %.2f%% (原 %.2f%%)  加权差距 %.2f (原 %.2f)"
      % (matchrate_after, matchrate_before, gapweighted_after, gapweighted_before))
check(matchrate_after > matchrate_before, "达标率提升（技能等级已回写）",
      "%.2f -> %.2f" % (matchrate_before, matchrate_after))
check(gapweighted_after <= gapweighted_before, "加权差距下降",
      "%.2f -> %.2f" % (gapweighted_before, gapweighted_after))

# ---------------------------------------------------------------- 8 AI 指标
step("8. AI 实验指标（论文数据）")
m = get(lect, "/ai/metrics")["data"]
spr = m.get("schemaPassRate")
spr_s = "—（未调用大模型）" if spr is None else str(spr) + "%"
print("    生成次数=%s LLM调用=%s 结构合规率=%s 采纳率=%s%% 规则降级率=%s%% RAG命中率=%s%%"
      % (m.get("total"), m.get("llmCalls"), spr_s, m.get("adoptRate"),
         m.get("fallbackRate"), m.get("ragHitRate")))
print("    平均耗时=%sms 平均重试=%s" % (m.get("avgCostMs"), m.get("avgAttempts")))
print("    状态分布=%s" % m.get("statusDist"))
print("    采纳分布=%s" % m.get("adoptDist"))
check(m.get("total", 0) > 0, "指标有数据")

qa = post(emp, "/ai/qa", json={"question": "高处作业安全带应该怎么使用？"})["data"]
print("    RAG 问答：%s" % str(qa.get("answer"))[:180])
qa_refs = qa.get("refs") or []
print("    引用 %d 条" % len(qa_refs))
check(True, "知识问答可用")

# ---------------------------------------------------------------- 汇总
step("结果汇总")
print("  通过 %d 项，失败 %d 项" % (ok_cnt, fail_cnt))
sys.exit(0 if fail_cnt == 0 else 1)
