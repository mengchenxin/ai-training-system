# -*- coding: utf-8 -*-
"""
生成毕设演示种子数据 -> 02-seed.sql
数据规模：5 部门 / 8 岗位 / 20 技能点 / 3 讲师 + 60 员工 / 26 课程 / 80 题 /
         21 知识库文档 / 6 Prompt 模板 / 示例计划与进度
设计原则：员工当前等级整体低于岗位目标等级（服从正态），保证差距分析有真实输出。
"""
import random
import json

random.seed(20261017)

PWD = "$2b$10$du5zlcl/LINr.5iOfup2R.IL072CJl9DcJVR/DVD96Q3iqDeC/ANK"  # 123456

out = []
w = out.append


def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def qjson(v):
    return "'" + json.dumps(v, ensure_ascii=False).replace("\\", "\\\\").replace("'", "''") + "'"


w("USE ai_training;")
w("SET NAMES utf8mb4;")
w("")
w("-- ============ 清空（保证脚本可重复执行）============")
w("SET FOREIGN_KEY_CHECKS = 0;")
for t in ["sys_dept", "sys_role", "sys_user", "sys_user_role", "post", "skill", "post_skill",
          "emp_skill", "train_need", "course", "train_plan", "plan_course", "train_progress",
          "question", "exam_paper", "exam_paper_question", "exam_record", "exam_answer",
          "answer_sheet", "kb_doc", "kb_chunk", "ai_prompt_template", "ai_gen_record",
          "ai_qa_log", "effect_eval"]:
    w("TRUNCATE TABLE %s;" % t)
w("SET FOREIGN_KEY_CHECKS = 1;")
w("")
w("-- ============ 角色 ============")
w("INSERT INTO sys_role (id, role_code, role_name, remark) VALUES")
w("  (1,'ADMIN','系统管理员','维护用户、岗位与技能模型'),")
w("  (2,'LECTURER','讲师','制定培训计划、授课、出题、审核方案、查看学员进度'),")
w("  (3,'EMPLOYEE','员工','查看个人差距与方案、上报进度、参加考核');")
w("")

# ---------------- 部门 ----------------
DEPTS = [
    (1, 0, "生产部", 2),
    (2, 0, "质量管理部", 5),
    (3, 0, "设备动力部", 8),
    (4, 0, "技术支持部", 11),
    (5, 0, "人力资源部", 14),
    (6, 1, "一号生产车间", 17),
    (7, 1, "二号生产车间", 20),
]
w("-- ============ 部门 ============")
w("INSERT INTO sys_dept (id,parent_id,dept_name,leader_id,sort_no) VALUES")
vals = [(d[0], d[1], q(d[2]), "NULL", d[3]) for d in DEPTS]
w(",\n".join("  (%s,%s,%s,%s,%s)" % v for v in vals) + ";")
w("")

# ---------------- 技能点 ----------------
SKILLS = [
    ("SK001", "安全生产规范", "安全", "掌握作业现场安全规程、劳动防护用品使用与违章行为识别"),
    ("SK002", "危险源辨识与风险评估", "安全", "能够辨识岗位危险源，使用 JSA/LEC 方法评估风险等级并制定控制措施"),
    ("SK003", "应急处置与逃生", "安全", "熟悉火灾、触电、机械伤害等突发事件的应急处置流程与疏散逃生要领"),
    ("SK004", "职业健康防护", "安全", "了解职业病危害因素、防护设施使用与职业健康监护要求"),
    ("SK005", "设备操作与标准化作业", "技术", "熟悉设备操作规程、开机点检、参数设定与标准作业顺序"),
    ("SK006", "设备维护保养", "技术", "掌握日常点检、润滑保养、易损件更换与一级保养规范"),
    ("SK007", "设备故障诊断与排除", "技术", "能够根据异常现象定位常见机械/电气故障并完成排除"),
    ("SK008", "质量检验与测量", "技术", "掌握检验标准、量具使用、抽样方法与检验记录规范"),
    ("SK009", "缺陷判定与原因分析", "技术", "能够判定典型缺陷等级，运用 5Why/鱼骨图分析根本原因"),
    ("SK010", "生产数据分析", "技术", "能够使用统计工具分析生产与质量数据并输出改进结论"),
    ("SK011", "工艺参数调整", "技术", "理解工艺参数对产品质量的影响，能够按规程调整并记录"),
    ("SK012", "电气安全与操作", "技术", "掌握低压电气安全操作、停送电挂牌与电气防护要求"),
    ("SK013", "自动化控制基础", "技术", "了解 PLC、传感器与自动化产线的基本原理与日常操作"),
    ("SK014", "团队协作", "通用", "跨班组沟通、任务协同、交接班信息传递与冲突处理"),
    ("SK015", "沟通表达", "通用", "能够清晰汇报工作、撰写交接记录与进行跨部门沟通"),
    ("SK016", "技术文档写作", "通用", "能够编写作业指导书、检验报告与问题分析报告"),
    ("SK017", "问题解决与改进", "通用", "运用 PDCA、8D 等方法推进问题闭环与持续改进"),
    ("SK018", "流程制度执行", "管理", "熟悉公司管理制度并能规范执行与记录留存"),
    ("SK019", "现场管理与带教", "管理", "掌握 5S、目视化管理与新人带教方法"),
    ("SK020", "成本与效率意识", "管理", "具备物料损耗控制、工时效率与成本改善意识"),
]
w("-- ============ 技能点 ============")
w("INSERT INTO skill (id,skill_code,skill_name,category,max_level,skill_desc) VALUES")
vals = [(i + 1, q(s[0]), q(s[1]), q(s[2]), 5, q(s[3])) for i, s in enumerate(SKILLS)]
w(",\n".join("  (%s,%s,%s,%s,%s,%s)" % v for v in vals) + ";")
w("")

# ---------------- 岗位 + 岗位技能要求 ----------------
# (code, name, dept_id, desc, [(skill_id, target, weight)])
POSTS = [
    ("P01", "生产操作工", 6, "负责产线设备操作、产品自检与现场 5S",
     [(1, 4, 1.5), (5, 4, 1.5), (6, 3, 1.0), (8, 3, 1.0), (14, 3, 0.8), (18, 3, 0.8)]),
    ("P02", "班组长", 6, "负责班组生产组织、现场管理与新人带教",
     [(1, 5, 1.5), (2, 4, 1.5), (3, 4, 1.2), (5, 4, 1.0), (14, 4, 1.2), (19, 4, 1.5), (20, 3, 0.8)]),
    ("P03", "设备维修技术员", 3, "负责设备点检、维护保养与故障排除",
     [(1, 4, 1.0), (6, 5, 1.8), (7, 5, 1.8), (12, 4, 1.5), (13, 3, 1.0), (17, 3, 0.8)]),
    ("P04", "质量检验员", 2, "负责来料、过程与成品检验及记录",
     [(1, 4, 1.0), (8, 5, 2.0), (9, 4, 1.5), (10, 3, 1.0), (15, 3, 0.8), (18, 3, 0.8)]),
    ("P05", "质量工程师", 2, "负责质量体系、缺陷分析与质量改进",
     [(8, 5, 1.5), (9, 5, 2.0), (10, 4, 1.5), (11, 3, 1.0), (16, 4, 1.2), (17, 4, 1.2)]),
    ("P06", "技术支持工程师", 4, "负责现场技术问题响应与工艺支持",
     [(5, 4, 1.0), (7, 4, 1.2), (10, 4, 1.2), (11, 4, 1.5), (13, 4, 1.2), (16, 3, 1.0), (17, 4, 1.2)]),
    ("P07", "培训专员", 5, "负责培训需求收集、计划执行与效果跟踪",
     [(1, 4, 1.0), (14, 4, 1.2), (15, 5, 1.8), (16, 4, 1.5), (18, 4, 1.2), (19, 4, 1.5)]),
    ("P08", "生产主管", 1, "负责生产计划、人员管理与成本效率",
     [(2, 4, 1.2), (4, 4, 1.0), (14, 4, 1.2), (17, 4, 1.5), (19, 4, 1.5), (20, 4, 1.5), (18, 5, 1.2)]),
]
w("-- ============ 岗位 ============")
w("INSERT INTO post (id,post_code,post_name,dept_id,post_desc) VALUES")
vals = [(i + 1, q(p[0]), q(p[1]), p[2], q(p[3])) for i, p in enumerate(POSTS)]
w(",\n".join("  (%s,%s,%s,%s,%s)" % v for v in vals) + ";")
w("")

w("-- ============ 岗位技能要求（技能模型）============")
rows = []
for i, p in enumerate(POSTS):
    for sk, tgt, wt in p[4]:
        rows.append("  (%d,%d,%d,%.2f)" % (i + 1, sk, tgt, wt))
w("INSERT INTO post_skill (post_id,skill_id,target_level,weight) VALUES")
w(",\n".join(rows) + ";")
w("")

# ---------------- 用户 ----------------
w("-- ============ 用户 ============")
users = []   # (id, username, real_name, dept_id, post_id, role_id)
users.append((1, "admin", "系统管理员", 5, 7, 1))
lect = [(2, "lecturer01", "陈静", 5, 7), (3, "lecturer02", "王海涛", 3, 3), (4, "lecturer03", "刘敏", 2, 4)]
for u in lect:
    users.append((u[0], u[1], u[2], u[3], u[4], 2))

SURNAME = "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜"
GIVEN = ["伟", "芳", "娜", "敏", "静", "强", "磊", "洋", "勇", "艳", "杰", "涛", "明", "超", "秀英", "霞", "平", "刚", "桂英", "建国"]
post_pool = [1] * 18 + [2] * 6 + [3] * 8 + [4] * 10 + [5] * 5 + [6] * 7 + [7] * 2 + [8] * 4
dept_of_post = {i + 1: p[2] for i, p in enumerate(POSTS)}
random.shuffle(post_pool)
emp_ids = []
uid = 5
for idx, pid in enumerate(post_pool):
    name = random.choice(SURNAME) + random.choice(GIVEN)
    users.append((uid, "emp%03d" % (idx + 1), name, dept_of_post[pid], pid, 3))
    emp_ids.append(uid)
    uid += 1

w("INSERT INTO sys_user (id,username,password,real_name,dept_id,post_id,phone,email) VALUES")
vals = []
for u in users:
    vals.append("  (%d,%s,%s,%s,%d,%d,%s,%s)" % (
        u[0], q(u[1]), q(PWD), q(u[2]), u[3], u[4],
        q("138%08d" % random.randint(0, 99999999)), q(u[1] + "@example.com")))
w(",\n".join(vals) + ";")
w("")
w("INSERT INTO sys_user_role (user_id,role_id) VALUES")
w(",\n".join("  (%d,%d)" % (u[0], u[5]) for u in users) + ";")
w("")

# ---------------- 员工技能档案 ----------------
w("-- ============ 员工技能档案（当前等级普遍低于岗位目标，形成真实差距）============")
rows = []
user_post = {u[0]: u[4] for u in users if u[5] == 3}
for ud, pid in user_post.items():
    req = POSTS[pid - 1][4]
    for sk, tgt, wt in req:
        lvl = int(round(random.gauss(tgt - 1.1, 0.9)))
        lvl = max(0, min(5, lvl))
        src = random.choice(["SELF", "SELF", "LECTURER", "EXAM"])
        rows.append("  (%d,%d,%d,%s,NOW())" % (ud, sk, lvl, q(src)))
w("INSERT INTO emp_skill (user_id,skill_id,current_level,assess_source,assess_time) VALUES")
w(",\n".join(rows) + ";")
w("")

# ---------------- 课程 ----------------
COURSES = []
cn = 1
for si, s in enumerate(SKILLS):
    sid = si + 1
    base_lv = 1 if s[2] in ("安全", "通用") else 2
    COURSES.append(("C%03d" % cn, s[1] + "基础", sid, s[2], base_lv,
                    random.choice([4.0, 6.0, 8.0]),
                    "面向%s岗位人员的「%s」基础课程，覆盖核心概念、操作要求与常见问题，配套案例练习。" % (s[2], s[1])))
    cn += 1
    if sid % 2 == 1:
        COURSES.append(("C%03d" % cn, s[1] + "进阶与实战", sid, s[2], min(5, base_lv + 2),
                        random.choice([10.0, 12.0, 16.0]),
                        "在基础之上深入「%s」的复杂场景处理、异常判断与改进方法，含实操演练与考核。" % s[1]))
        cn += 1

w("-- ============ 课程 ============")
w("INSERT INTO course (id,course_code,course_name,skill_id,category,level,duration_hours,intro,resource_url) VALUES")
vals = []
for i, c in enumerate(COURSES):
    vals.append("  (%d,%s,%s,%d,%s,%d,%.1f,%s,%s)" % (
        i + 1, q(c[0]), q(c[1]), c[2], q(c[3]), c[4], c[5], q(c[6]),
        q("https://training.example.com/course/" + c[0])))
w(",\n".join(vals) + ";")
w("")

# ---------------- 题库 ----------------
w("-- ============ 题库（每技能点：单选*2 + 判断*1 + 简答*1）============")
qrows = []
qn = 0
for si, s in enumerate(SKILLS):
    sid = si + 1
    name = s[1]
    # 单选 1
    qn += 1
    opts = [{"key": "A", "text": "严格按%s的标准流程执行并留存记录" % name},
            {"key": "B", "text": "根据个人经验灵活处理，事后口头说明"},
            {"key": "C", "text": "由班组长代为执行，本人无需掌握"},
            {"key": "D", "text": "遇到问题再临时学习，不必提前掌握"}]
    qrows.append((qn, sid, name, "SINGLE",
                  "关于「%s」，下列做法最符合规范要求的是？" % name,
                  qjson(opts), "A", "标准流程执行并留痕是可追溯的基本要求。", 5.0, 1))
    # 单选 2
    qn += 1
    opts = [{"key": "A", "text": "暂停作业并上报，按规程确认安全后恢复"},
            {"key": "B", "text": "继续作业，等下班后统一上报"},
            {"key": "C", "text": "自行拆卸检查，节省维修时间"},
            {"key": "D", "text": "忽略异常，观察是否自行恢复"}]
    qrows.append((qn, sid, name, "SINGLE",
                  "在「%s」相关作业中发现异常情况，正确的首要处置是？" % name,
                  qjson(opts), "A", "异常先停机上报，是安全与质量管理的通用底线。", 5.0, 2))
    # 判断
    qn += 1
    opts = [{"key": "A", "text": "正确"}, {"key": "B", "text": "错误"}]
    qrows.append((qn, sid, name, "JUDGE",
                  "「%s」只要最终结果合格，过程记录可以事后补填。" % name,
                  qjson(opts), "B", "过程记录必须实时、真实，事后补填违反可追溯原则。", 5.0, 1))
    # 简答
    qn += 1
    qrows.append((qn, sid, name, "SHORT",
                  "请结合本岗位实际，简述「%s」的关键控制点，并各举一个执行不到位的典型表现。" % name,
                  None,
                  "关键控制点：1）作业前按标准确认条件与防护；2）作业中按规程参数执行并记录；"
                  "3）作业后自检与交接。典型表现：跳步操作、记录滞后或缺失、异常未上报。",
                  "评分要点：控制点覆盖作业前/中/后三段（6分）；典型表现贴合岗位（4分）。", 10.0, 3))

w("INSERT INTO question (id,skill_id,knowledge_point,question_type,content,options,answer,analysis,score,difficulty) VALUES")
vals = []
for r in qrows:
    vals.append("  (%d,%d,%s,%s,%s,%s,%s,%s,%.1f,%d)" % (
        r[0], r[1], q(r[2]), q(r[3]), q(r[4]), r[5] if r[5] else "NULL",
        q(r[6]), q(r[7]), r[8], r[9]))
w(",\n".join(vals) + ";")
w("")

# ---------------- 知识库 ----------------
w("-- ============ 知识库文档与片段（RAG 语料）============")
docs = []
chunks = []
cid = 1
for si, s in enumerate(SKILLS):
    dn = "《%s岗位技能标准》%s" % (s[2], s[1])
    docs.append((si + 1, dn, "SKILL_STANDARD", "企业岗位技能标准（内部资料）", 2))
    chunks.append((cid, si + 1, 0,
                   "%s（%s类，等级 1-5）：%s。等级 1 为知晓基本概念；等级 2 为能在指导下完成；"
                   "等级 3 为能独立完成常规作业；等级 4 为能处理复杂与异常场景并指导他人；"
                   "等级 5 为能制定标准、开展改进与培训。岗位达标要求由岗位技能要求表确定。" % (s[1], s[2], s[3])))
    cid += 1
    chunks.append((cid, si + 1, 1,
                   "培训与考核建议：针对「%s」等级提升，建议先安排基础课程建立概念，"
                   "再通过现场实操与案例研讨强化，最后以题库测验（含简答题）验证。"
                   "培训后等级提升 1 级以上视为有效，未达标者进入补训环节。" % s[1]))
    cid += 1

# 培训制度文档
did = len(SKILLS) + 1
docs.append((did, "《员工培训管理办法》", "POLICY", "公司人力资源制度汇编", 3))
chunks.append((cid, did, 0,
               "培训需求来源于两个渠道：一是岗位技能差距自动分析，系统按「差距值×技能权重」排序生成培训需求；"
               "二是员工或讲师主动提报。所有需求需经讲师审核，审核通过后纳入培训计划。"))
cid += 1
chunks.append((cid, did, 1,
               "培训计划分为个人、部门、岗位与批量四类，状态依次为草稿、待审核、已发布、进行中、已结束。"
               "计划发布后员工方可上报进度，讲师按周跟踪完成率，完成率低于 60% 且接近截止日期的计划列为拖期风险。"))
cid += 1
chunks.append((cid, did, 2,
               "考核采用培训前后测对比：前测在计划开始前进行，后测在计划结束后进行。"
               "前后测使用同一技能点范围内的等价试卷。成绩提升值与技能差距缩小率共同作为效果诊断依据，"
               "结论分为优秀、达标、未达标三档，未达标者系统自动生成补训方案。"))
cid += 1

# 岗位说明文档
did2 = did + 1
docs.append((did2, "《关键岗位说明书汇编》", "POST_DESC", "公司岗位体系文件", 2))
chunks.append((cid, did2, 0,
               "生产操作工：负责产线设备操作、产品自检与现场 5S，需达到安全生产规范等级 4、"
               "设备操作与标准化作业等级 4。班组长在此基础上还需具备危险源辨识等级 4 与现场管理带教等级 4。"))
cid += 1
chunks.append((cid, did2, 1,
               "质量检验员需达到质量检验与测量等级 5、缺陷判定与原因分析等级 4；"
               "质量工程师进一步要求生产数据分析等级 4 与技术文档写作等级 4，能独立主导质量改进项目。"))
cid += 1

w("INSERT INTO kb_doc (id,doc_name,doc_type,source,chunk_count,status) VALUES")
w(",\n".join("  (%d,%s,%s,%s,%d,'READY')" % (d[0], q(d[1]), q(d[2]), q(d[3]), d[4]) for d in docs) + ";")
w("")
w("INSERT INTO kb_chunk (id,doc_id,chunk_index,content,token_count) VALUES")
w(",\n".join("  (%d,%d,%d,%s,%d)" % (c[0], c[1], c[2], q(c[3]), len(c[3])) for c in chunks) + ";")
w("")

# ---------------- Prompt 模板 ----------------
PLAN_PROMPT = """你是一名企业培训专家。请根据下面的员工技能差距数据与候选课程清单，生成个性化培训方案。

【员工信息】
姓名：{{realName}}；岗位：{{postName}}；部门：{{deptName}}

【技能差距（已按优先级排序，gap=目标等级-当前等级，priority=gap×权重）】
{{gapTable}}

【候选课程（已由规则引擎按差距优先级召回，课程ID不可虚构）】
{{courseCandidates}}

【知识库依据】
{{ragContext}}

【要求】
1. 只能从候选课程中选择，courseId 必须来自候选清单；
2. 按 gap 从大到小安排学习顺序，先补高权重差距；
3. 总学时控制在 {{totalHours}} 学时以内；
4. 每门课必须给出 reason，说明它解决哪个技能差距、依据是什么；
5. 输出严格为 JSON，不要任何解释性文字或 Markdown 代码块。

【输出格式】
{"summary":"方案总体说明，150字以内","totalHours":数字,"courses":[{"courseId":数字,"seqNo":数字,"hours":数字,"coverSkill":"技能名称","gapBefore":数字,"reason":"安排依据"}]}"""

DIAG_PROMPT = """你是一名培训效果评估专家。请根据以下数据给出效果诊断结论。

【员工】{{realName}}（{{postName}}）
【培训计划】{{planName}}
【前测成绩】{{preScore}}  【后测成绩】{{postScore}}  【提升】{{improve}}
【计划完成率】{{finishRate}}%
【培训前技能差距】{{gapBefore}}
【培训后技能差距】{{gapAfter}}
【差距缩小率】{{gapReduce}}%

【要求】判断培训是否有效，指出仍存在的主要短板及其业务影响，给出 2-3 条具体改进建议。
输出 JSON：{"result":"EXCELLENT|PASS|FAIL","diagnosis":"诊断结论，200字以内","weakPoints":["短板1","短板2"],"suggestions":["建议1","建议2"]}"""

RETRAIN_PROMPT = """你是一名培训方案设计专家。员工 {{realName}}（{{postName}}）在培训后仍未达标，需要生成补训方案。

【未达标原因】{{diagnosis}}
【剩余技能差距】{{remains}}
【前测/后测成绩】{{preScore}} / {{postScore}}
【可用课程】{{courseCandidates}}

【要求】针对剩余差距设计补训方案，优先安排差距最大项；给出补训周期建议与验收方式。
只从可用课程中选择。输出 JSON：
{"summary":"补训思路，100字以内","duration":"建议周期，如 2周","courses":[{"courseId":数字,"hours":数字,"reason":"安排依据"}],"acceptance":"验收方式"}"""

QA_PROMPT = """你是企业培训知识助手。请仅依据下面检索到的知识片段回答用户问题，不要编造。

【知识片段】
{{ragContext}}

【用户问题】{{question}}

【要求】回答简洁准确；若知识片段不足以回答，明确说明"知识库中未收录该内容"，不要猜测。
在回答末尾用「参考：文档名」标注引用来源。"""

PAPER_PROMPT = """你是组卷专家。请根据技能范围与难度分布要求，从题库中选择题目组卷。
【技能范围】{{skillNames}}
【难度分布】简单 {{easyCount}} 题 / 中等 {{mediumCount}} 题 / 困难 {{hardCount}} 题
【可用题目】{{questionPool}}
【要求】只从可用题目中选择 questionId，覆盖各技能点。输出 JSON：
{"paperName":"试卷名称","questions":[{"questionId":数字,"score":数字,"reason":"选取理由"}]}"""

JUDGE_PROMPT = """你是严谨的阅卷老师，请为简答题评分。

【题目】{{question}}
【参考答案】{{answer}}
【评分要点】{{analysis}}
【满分】{{fullScore}}
【学员作答】{{userAnswer}}

【要求】按参考要点给分，允许同义表述；给分需保守客观。输出 JSON：
{"score":数字,"reason":"评分理由，80字以内","missing":"遗漏的要点"}"""

TPLS = [
    ("PLAN_GEN_V1", "个性化培训方案生成", "PLAN_GEN", PLAN_PROMPT, 0.3, 2000),
    ("EFFECT_DIAG_V1", "培训效果诊断", "EFFECT_DIAG", DIAG_PROMPT, 0.2, 1200),
    ("RETRAIN_V1", "补训方案生成", "RETRAIN", RETRAIN_PROMPT, 0.3, 1200),
    ("QA_V1", "知识库问答", "QA", QA_PROMPT, 0.1, 1000),
    ("PAPER_GEN_V1", "按技能自动组卷", "PAPER_GEN", PAPER_PROMPT, 0.2, 1500),
    ("JUDGE_V1", "主观题辅助评分", "JUDGE", JUDGE_PROMPT, 0.1, 600),
]
w("-- ============ Prompt 模板 ============")
w("INSERT INTO ai_prompt_template (template_code,template_name,scene,content,model,temperature,max_tokens,version) VALUES")
w(",\n".join("  (%s,%s,%s,%s,%s,%.2f,%d,1)" % (
    q(t[0]), q(t[1]), q(t[2]), q(t[3]), q("deepseek-chat"), t[4], t[5]) for t in TPLS) + ";")
w("")

# ---------------- 示例培训计划与进度 ----------------
w("-- ============ 示例培训计划（部门计划）============")
plan_courses = [(1, 3), (2, 5), (3, 9)]  # course_id 对应 skill 1/3/5
w("INSERT INTO train_plan (id,plan_no,plan_name,plan_type,target_dept_id,start_date,end_date,status,gen_type,creator_id,auditor_id,audit_time) VALUES")
w("  (1,'TP20261001001','一号车间安全生产与设备操作提升计划','DEPT',6,'2026-08-01','2026-10-31','RUNNING','MANUAL',2,2,NOW());")
w("")
w("INSERT INTO plan_course (plan_id,course_id,seq_no,expect_hours,cover_skill_id,gap_before,plan_reason) VALUES")
w("  (1,1,1,8.0,1,2,'安全生产规范为岗位核心要求且当前差距最大，优先安排'),")
w("  (1,3,2,8.0,3,2,'应急处置为班组必备能力，紧随安全基础课之后'),")
w("  (1,9,3,8.0,5,1,'设备操作差距 1 级，安排在安全类课程之后');")
w("")

# 进度：一号车间员工（post 1/2）
w("-- ============ 示例进度上报 ============")
rows = []
today_off = 0
for ud, pid in user_post.items():
    if POSTS[pid - 1][2] != 6:
        continue
    for course_id, pct in [(1, random.choice([100, 100, 100, 80, 60])),
                           (3, random.choice([100, 60, 40, 20, 0])),
                           (9, random.choice([30, 0, 0, 60]))]:
        status = "FINISHED" if pct == 100 else ("LEARNING" if pct > 0 else "NOT_STARTED")
        rows.append("  (1,%d,%d,%.2f,%s,%.1f,NOW())" % (course_id, ud, pct, q(status), pct / 100 * 8))
if rows:
    w("INSERT INTO train_progress (plan_id,course_id,user_id,progress_percent,status,spent_hours,report_time) VALUES")
    w(",\n".join(rows) + ";")
w("")

# 培训需求
w("-- ============ 培训需求 ============")
w("INSERT INTO train_need (need_title,dept_id,post_id,skill_id,need_desc,source,priority,submitter_id,audit_status) VALUES")
w("  ('一号车间安全生产规范专项培训',6,1,1,'现场检查发现部分员工劳保用品穿戴不规范，需专项强化','GAP',1,2,'APPROVED'),")
w("  ('质量检验员量具使用与缺陷判定提升',2,4,9,'近期客户投诉中外观缺陷漏检占比偏高','LECTURER',1,4,'APPROVED'),")
w("  ('设备维修技术员故障诊断能力提升',3,3,7,'设备突发故障平均修复时间偏长','EMP',2,3,'PENDING'),")
w("  ('班组长现场管理与带教方法培训',6,2,19,'新任班组长带教能力不足，新人上岗周期长','GAP',2,2,'PENDING'),")
w("  ('新员工职业健康防护知识普及',6,1,4,'年度职业健康体检前开展防护知识普及','EMP',3,2,'REJECTED');")
w("")

with open("./02-seed.sql", "w", encoding="utf-8") as f:
    f.write("\n".join(out))

print("生成完成: 02-seed.sql")
print("用户数=%d 员工=%d 课程=%d 题目=%d 知识片段=%d 进度=%d" % (
    len(users), len(emp_ids), len(COURSES), len(qrows), len(chunks), len(rows)))
