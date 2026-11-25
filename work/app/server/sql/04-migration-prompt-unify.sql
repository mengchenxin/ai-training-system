-- =====================================================================
--  增量迁移 02：系统提示词入库 + 清理未使用的模板
--
--  背景：评审指出「Prompt 统一管理、不在代码硬编码」只做到一半——
--        模板入库了 4 个场景，但 system prompt 仍写死在 4 个 Service 里；
--        另有 RETRAIN / PAPER_GEN 两个模板入库却从未被代码读取，
--        导致「模板数」与「实际调用数」不一致。
--
--  本次改动：
--    1. ai_prompt_template 增加 system_content 列，系统提示词一并入库；
--    2. 回填 4 个在用场景的系统提示词；
--    3. 删除从未被调用的 RETRAIN / PAPER_GEN 模板（补训与组卷均为规则实现）。
--
--  执行：mysql -uroot -p ai_training < 04-migration-prompt-unify.sql
-- =====================================================================

USE ai_training;

ALTER TABLE ai_prompt_template
  ADD COLUMN system_content TEXT NULL
  COMMENT '系统提示词，与 content（用户提示词）分离管理，避免硬编码在 Java 里'
  AFTER content;

UPDATE ai_prompt_template
   SET system_content = '你是企业培训方案设计专家。你只能从给定的候选课程中选择，输出必须是合法 JSON，不要输出任何解释性文字、Markdown 代码块或注释。'
 WHERE scene = 'PLAN_GEN';

UPDATE ai_prompt_template
   SET system_content = '你是企业培训知识助手，回答必须基于给定资料，不得编造。'
 WHERE scene = 'QA';

UPDATE ai_prompt_template
   SET system_content = '你是严谨的阅卷老师，只输出 JSON。'
 WHERE scene = 'JUDGE';

UPDATE ai_prompt_template
   SET system_content = '你是培训效果评估专家，只输出 JSON。'
 WHERE scene = 'EFFECT_DIAG';

-- 这两个场景在实际业务里由规则引擎完成（组卷按知识点+难度抽题、补训按剩余差距排课），
-- 从未发起过大模型调用，保留模板只会让「模板数量」虚高
DELETE FROM ai_prompt_template WHERE scene IN ('RETRAIN', 'PAPER_GEN');

SELECT scene, template_code, LEFT(system_content, 20) AS system_head
  FROM ai_prompt_template
 ORDER BY scene;
