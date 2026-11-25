-- =====================================================================
--  增量迁移 05：纯大模型对照组独立模板（PLAN_GEN_FREE）
--
--  背景：Evaluate 指出「Prompt 统一管理」在三组对比里破了个洞——
--        LLM_ONLY 分支既没有走 systemPrompt()，又复用了 PLAN_GEN 的用户提示词，
--        而 PLAN_GEN 正文写死「只能从候选课程中选择，courseId 必须来自候选清单」，
--        与纯大模型组「放开候选集、自主设计」的实验意图直接矛盾。
--
--        实际后果有两层：
--          1. 在模板表里改 PLAN_GEN，混合模式会变、纯大模型组不变（system 走硬编码）；
--          2. 纯大模型组拿到的是「约束型」用户提示词，只能靠代码后面追加一段
--             「不要局限于上面列出的课程」去覆盖，属于提示词自相打架。
--
--  本次改动：
--    1. 新增 scene=PLAN_GEN_FREE 的模板，用户提示词与系统提示词均按「无候选集约束」重写；
--    2. 代码侧 llmOnly() 改走 systemPrompt("PLAN_GEN_FREE", ...)，
--       生成的记录 template_id 也按模式区分，便于统计两组各自用了哪版模板。
--
--  执行：mysql -uroot -p ai_training < 05-migration-plan-gen-free.sql
--  幂等：重复执行只覆盖内容，不会插入重复记录。
-- =====================================================================

USE ai_training;

INSERT INTO ai_prompt_template
  (template_code, template_name, scene, content, system_content,
   model, temperature, max_tokens, version, status)
VALUES
  ('PLAN_GEN_FREE_V1', '纯大模型方案生成（无候选集约束）', 'PLAN_GEN_FREE',
   '你是一名企业培训专家。请根据下面的员工技能差距数据，自主设计一份个性化培训方案。

【员工信息】
姓名：{{realName}}；岗位：{{postName}}；部门：{{deptName}}

【技能差距（已按优先级排序，gap=目标等级-当前等级，priority=gap×权重）】
{{gapTable}}

【课程库（仅作命名与学时参考，不构成选择限制）】
{{courseCandidates}}

【知识库依据】
{{ragContext}}

【要求】
1. 不限课程范围：可结合岗位要求与技能差距自主判断学什么、先学什么，课程名称可用课程库中的课程，也可按专业判断给出更合适的名称；
2. 按 gap 从大到小安排学习顺序，先补高权重差距；
3. 总学时控制在 {{totalHours}} 学时以内，超出视为不合格；
4. 每门课必须给出 reason，说明它解决哪个技能差距、为什么排在此时；
5. 学时按经验估计，但需与课程体量相称；
6. 输出严格为 JSON，不要任何解释性文字或 Markdown 代码块。

【输出格式】
{"summary":"方案总体说明，150字以内","totalHours":数字,"courses":[{"courseName":"课程名称","hours":数字,"reason":"安排依据","courseId":数字}]}',
   '你是企业培训方案设计专家。请根据员工的岗位要求与技能差距，自主设计一份培训方案，包括课程组合、学习顺序与学时安排，并为每门课程说明安排依据。输出必须是合法 JSON，不要输出任何解释性文字、Markdown 代码块或注释。',
   'deepseek-chat', 0.30, 2000, 1, 1)
ON DUPLICATE KEY UPDATE
  template_name  = VALUES(template_name),
  scene          = VALUES(scene),
  content        = VALUES(content),
  system_content = VALUES(system_content);

SELECT template_code, scene, LENGTH(content) AS content_len,
       LEFT(system_content, 24) AS system_head
  FROM ai_prompt_template
 WHERE scene IN ('PLAN_GEN', 'PLAN_GEN_FREE')
 ORDER BY scene;
