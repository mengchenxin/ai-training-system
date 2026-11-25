-- =====================================================================
--  增量迁移脚本：补齐「三组对比实验 + 效果盲评」所需的字段
--  适用对象：已经用 01-schema.sql 建过库、不想重建数据的库
--  执行方式：mysql -uroot -p ai_training < 03-migration-optimization.sql
--  幂等性：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报
--          1060 (Duplicate column name)，报错即代表已迁移过，忽略即可。
-- =====================================================================

USE ai_training;

-- 生成模式：三组对比实验的分组键（RULE / HYBRID / LLM_ONLY）
ALTER TABLE ai_gen_record
  ADD COLUMN gen_mode VARCHAR(16) NULL
  COMMENT '生成模式：RULE/HYBRID/LLM_ONLY，三组对比实验的分组键' AFTER attempt_count;

-- 幻觉课程数：纯大模型组输出中课程库不存在的课程数量
ALTER TABLE ai_gen_record
  ADD COLUMN hallucination_count INT NULL
  COMMENT '幻觉课程数（仅 LLM_ONLY 有意义）' AFTER gen_mode;

-- 盲评得分：人工在隐去模式标签的条件下按 1-5 分打分
ALTER TABLE ai_gen_record
  ADD COLUMN blind_score TINYINT NULL
  COMMENT '盲评得分 1-5（人工在不知模式的条件下打分）' AFTER hallucination_count;

-- 历史数据回填：老记录只有 SUCCESS / RULE_ONLY / FALLBACK 三类状态，
-- 按状态推断其模式，避免统计时被归到 UNKNOWN
UPDATE ai_gen_record
   SET gen_mode = 'RULE'
 WHERE gen_mode IS NULL AND status = 'RULE_ONLY';

UPDATE ai_gen_record
   SET gen_mode = 'HYBRID'
 WHERE gen_mode IS NULL
   AND status IN ('SUCCESS', 'SCHEMA_FAILED', 'API_ERROR', 'FALLBACK');

UPDATE ai_gen_record SET hallucination_count = 0 WHERE hallucination_count IS NULL;

-- 便于按模式做分组统计与盲评取数
CREATE INDEX idx_gen_mode ON ai_gen_record (gen_mode);
