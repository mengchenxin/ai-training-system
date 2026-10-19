-- =====================================================================
-- 基于 Spring Boot 的 AI 培训计划管理系统 —— 数据库结构（MySQL 8.0）
-- 角色：ADMIN 管理员 / LECTURER 讲师 / EMPLOYEE 员工
-- 说明：
--   1. 命名规范：表名小写下划线；主键统一 id bigint auto_increment；
--      审计字段 create_time / update_time / del_flag（0 正常，1 删除）。
--   2. 共 24 张表，分为 5 组：组织权限 / 岗位技能 / 培训计划 /
--      执行考核 / AI 增强。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS ai_training
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE ai_training;

-- =====================================================================
-- 1. 组织与权限
-- =====================================================================

DROP TABLE IF EXISTS sys_dept;
CREATE TABLE sys_dept (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  parent_id     BIGINT       NOT NULL DEFAULT 0      COMMENT '上级部门ID',
  dept_name     VARCHAR(64)  NOT NULL                COMMENT '部门名称',
  leader_id     BIGINT       NULL                    COMMENT '部门负责人用户ID',
  sort_no       INT          NOT NULL DEFAULT 0      COMMENT '排序',
  status        TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0停用',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag      TINYINT      NOT NULL DEFAULT 0      COMMENT '0正常 1删除',
  PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='部门表';

DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  role_code     VARCHAR(64)  NOT NULL                COMMENT '角色标识：ADMIN/LECTURER/EMPLOYEE',
  role_name     VARCHAR(64)  NOT NULL                COMMENT '角色名称：管理员/讲师/员工',
  remark        VARCHAR(255) NULL,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB COMMENT='角色表';

DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL                COMMENT '登录账号',
  password      VARCHAR(128) NOT NULL                COMMENT 'BCrypt 密文',
  real_name     VARCHAR(64)  NOT NULL                COMMENT '姓名',
  dept_id       BIGINT       NULL                    COMMENT '所属部门',
  post_id       BIGINT       NULL                    COMMENT '所属岗位',
  phone         VARCHAR(20)  NULL,
  email         VARCHAR(64)  NULL,
  avatar        VARCHAR(255) NULL,
  status        TINYINT      NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
  last_login_at DATETIME     NULL,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_dept (dept_id),
  KEY idx_post (post_id)
) ENGINE=InnoDB COMMENT='用户表';

DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
  id       BIGINT NOT NULL AUTO_INCREMENT,
  user_id  BIGINT NOT NULL,
  role_id  BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB COMMENT='用户角色关联表';

-- =====================================================================
-- 2. 岗位与技能（技能差距分析的数据基础）
-- =====================================================================

DROP TABLE IF EXISTS post;
CREATE TABLE post (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  post_code     VARCHAR(64)  NOT NULL                COMMENT '岗位编码',
  post_name     VARCHAR(64)  NOT NULL                COMMENT '岗位名称',
  dept_id       BIGINT       NULL                    COMMENT '归属部门',
  post_desc     VARCHAR(500) NULL                    COMMENT '岗位职责说明',
  status        TINYINT      NOT NULL DEFAULT 1,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_post_code (post_code)
) ENGINE=InnoDB COMMENT='岗位表';

DROP TABLE IF EXISTS skill;
CREATE TABLE skill (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  skill_code    VARCHAR(64)  NOT NULL                COMMENT '技能编码',
  skill_name    VARCHAR(64)  NOT NULL                COMMENT '技能名称',
  category      VARCHAR(32)  NOT NULL                COMMENT '技能类别：安全/技术/通用/管理',
  max_level     INT          NOT NULL DEFAULT 5      COMMENT '等级上限（默认5级）',
  skill_desc    VARCHAR(500) NULL                    COMMENT '技能说明，用于知识库与Prompt上下文',
  status        TINYINT      NOT NULL DEFAULT 1,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_skill_code (skill_code),
  KEY idx_category (category)
) ENGINE=InnoDB COMMENT='技能点表';

DROP TABLE IF EXISTS post_skill;
CREATE TABLE post_skill (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  post_id       BIGINT      NOT NULL                COMMENT '岗位ID',
  skill_id      BIGINT      NOT NULL                COMMENT '技能ID',
  target_level  INT         NOT NULL                COMMENT '岗位要求等级',
  weight        DECIMAL(4,2) NOT NULL DEFAULT 1.00  COMMENT '该技能对本岗位的重要度权重',
  create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag      TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_post_skill (post_id, skill_id),
  KEY idx_skill (skill_id)
) ENGINE=InnoDB COMMENT='岗位技能要求表（技能模型）';

DROP TABLE IF EXISTS emp_skill;
CREATE TABLE emp_skill (
  id             BIGINT      NOT NULL AUTO_INCREMENT,
  user_id        BIGINT      NOT NULL               COMMENT '员工ID',
  skill_id       BIGINT      NOT NULL               COMMENT '技能ID',
  current_level  INT         NOT NULL DEFAULT 0     COMMENT '当前等级',
  assess_source  VARCHAR(32) NOT NULL DEFAULT 'SELF'
                 COMMENT '评定来源：SELF自评/LECTURER讲师评定/EXAM考核得出',
  assess_time    DATETIME    NULL                   COMMENT '评定时间',
  create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag       TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_skill (user_id, skill_id),
  KEY idx_skill (skill_id)
) ENGINE=InnoDB COMMENT='员工技能档案表';

-- =====================================================================
-- 3. 培训需求、课程与计划
-- =====================================================================

DROP TABLE IF EXISTS train_need;
CREATE TABLE train_need (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  need_title    VARCHAR(128) NOT NULL                COMMENT '需求标题',
  dept_id       BIGINT       NULL,
  post_id       BIGINT       NULL,
  skill_id      BIGINT       NULL                    COMMENT '对应技能点',
  need_desc     VARCHAR(500) NULL                    COMMENT '需求描述',
  source        VARCHAR(32)  NOT NULL DEFAULT 'GAP'
                COMMENT '来源：GAP技能差距自动生成/EMP员工上报/LECTURER讲师提报',
  priority      INT          NOT NULL DEFAULT 2      COMMENT '优先级 1高 2中 3低',
  submitter_id  BIGINT       NULL                    COMMENT '提报人',
  audit_status  VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                COMMENT '审核状态：PENDING/APPROVED/REJECTED',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_dept_post (dept_id, post_id),
  KEY idx_status (audit_status)
) ENGINE=InnoDB COMMENT='培训需求表';

DROP TABLE IF EXISTS course;
CREATE TABLE course (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  course_code    VARCHAR(64)  NOT NULL               COMMENT '课程编码',
  course_name    VARCHAR(128) NOT NULL               COMMENT '课程名称',
  skill_id       BIGINT       NULL                   COMMENT '主要覆盖技能点',
  category       VARCHAR(32)  NULL                   COMMENT '课程类别',
  level          INT          NOT NULL DEFAULT 1     COMMENT '适用等级/难度',
  duration_hours DECIMAL(5,1) NOT NULL DEFAULT 0     COMMENT '建议学时',
  intro          TEXT         NULL                   COMMENT '课程简介（同时作为知识库语料）',
  resource_url   VARCHAR(255) NULL                   COMMENT '课程资源地址',
  status         TINYINT      NOT NULL DEFAULT 1,
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_code (course_code),
  KEY idx_skill (skill_id)
) ENGINE=InnoDB COMMENT='课程/培训资源表';

DROP TABLE IF EXISTS train_plan;
CREATE TABLE train_plan (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  plan_no        VARCHAR(64)  NOT NULL               COMMENT '计划编号',
  plan_name      VARCHAR(128) NOT NULL               COMMENT '计划名称',
  plan_type      VARCHAR(16)  NOT NULL
                 COMMENT '类型：PERSONAL个人/DEPT部门/POST岗位/BATCH批量',
  target_user_id BIGINT       NULL                   COMMENT '个人计划的目标员工',
  target_dept_id BIGINT       NULL                   COMMENT '部门计划的目标部门',
  target_post_id BIGINT       NULL                   COMMENT '岗位计划的目标岗位',
  start_date     DATE         NULL,
  end_date       DATE         NULL,
  status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'
                 COMMENT '状态：DRAFT草稿/PENDING待审/PUBLISHED已发布/RUNNING进行中/FINISHED已结束/CANCELED已取消',
  gen_type       VARCHAR(16)  NOT NULL DEFAULT 'MANUAL'
                 COMMENT '方案来源：MANUAL人工/AI_RULE规则/AI_LLM大模型/AI_HYBRID规则+大模型',
  ai_record_id   BIGINT       NULL                   COMMENT 'AI 生成记录ID（可解释性溯源）',
  creator_id     BIGINT       NULL,
  auditor_id     BIGINT       NULL                   COMMENT '审核人',
  audit_time     DATETIME     NULL,
  audit_remark   VARCHAR(500) NULL,
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plan_no (plan_no),
  KEY idx_target_user (target_user_id),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='培训计划表';

DROP TABLE IF EXISTS plan_course;
CREATE TABLE plan_course (
  id             BIGINT      NOT NULL AUTO_INCREMENT,
  plan_id        BIGINT      NOT NULL               COMMENT '计划ID',
  course_id      BIGINT      NOT NULL               COMMENT '课程ID',
  seq_no         INT         NOT NULL DEFAULT 1     COMMENT '建议学习顺序',
  expect_hours   DECIMAL(5,1) NULL                  COMMENT '计划学时',
  cover_skill_id BIGINT      NULL                   COMMENT '本课程解决的目标技能点',
  gap_before     INT         NULL                   COMMENT '安排时的差距值（等级差）',
  plan_reason    VARCHAR(500) NULL                  COMMENT '安排依据（AI生成时的说明，支撑可解释性）',
  create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plan_course (plan_id, course_id),
  KEY idx_plan (plan_id)
) ENGINE=InnoDB COMMENT='计划-课程关联表';

-- =====================================================================
-- 4. 培训执行与考核
-- =====================================================================

DROP TABLE IF EXISTS train_progress;
CREATE TABLE train_progress (
  id               BIGINT      NOT NULL AUTO_INCREMENT,
  plan_id          BIGINT      NOT NULL,
  plan_course_id   BIGINT      NULL                  COMMENT '关联的计划课程行',
  course_id        BIGINT      NOT NULL,
  user_id          BIGINT      NOT NULL              COMMENT '上报人/学习人',
  progress_percent DECIMAL(5,2) NOT NULL DEFAULT 0   COMMENT '完成百分比',
  status           VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED'
                   COMMENT '状态：NOT_STARTED未开始/LEARNING学习中/FINISHED已完成',
  spent_hours      DECIMAL(5,1) NULL                 COMMENT '实际投入学时',
  report_time      DATETIME    NULL                  COMMENT '最近上报时间',
  remark           VARCHAR(500) NULL,
  create_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plan_course_user (plan_id, course_id, user_id),
  KEY idx_user (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='培训进度表（风险预测模型的数据来源）';

DROP TABLE IF EXISTS question;
CREATE TABLE question (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  skill_id        BIGINT       NULL                  COMMENT '所属技能点',
  knowledge_point VARCHAR(128) NULL                  COMMENT '知识点',
  question_type   VARCHAR(16)  NOT NULL
                  COMMENT '题型：SINGLE单选/MULTI多选/JUDGE判断/SHORT简答',
  content         TEXT         NOT NULL              COMMENT '题干',
  options         JSON         NULL                  COMMENT '选项：[{"key":"A","text":"..."}]',
  answer          TEXT         NULL                  COMMENT '参考答案',
  analysis        VARCHAR(500) NULL                  COMMENT '解析',
  score           DECIMAL(5,1) NOT NULL DEFAULT 5    COMMENT '建议分值',
  difficulty      INT          NOT NULL DEFAULT 2    COMMENT '难度 1易 2中 3难',
  gen_type        VARCHAR(16)  NOT NULL DEFAULT 'MANUAL'
                  COMMENT '来源：MANUAL人工/AI大模型生成',
  status          TINYINT      NOT NULL DEFAULT 1,
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_skill (skill_id),
  KEY idx_type_diff (question_type, difficulty)
) ENGINE=InnoDB COMMENT='题库表';

DROP TABLE IF EXISTS exam_paper;
CREATE TABLE exam_paper (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  paper_name    VARCHAR(128) NOT NULL,
  plan_id       BIGINT       NULL                    COMMENT '关联培训计划',
  total_score   DECIMAL(6,1) NOT NULL DEFAULT 100,
  pass_score    DECIMAL(6,1) NOT NULL DEFAULT 60,
  gen_type      VARCHAR(16)  NOT NULL DEFAULT 'MANUAL'
                COMMENT '组卷方式：MANUAL人工/AI_BY_SKILL按技能自动',
  rule_json     JSON         NULL                    COMMENT '组卷规则：知识点与难度分布',
  status        TINYINT      NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_plan (plan_id)
) ENGINE=InnoDB COMMENT='试卷表';

DROP TABLE IF EXISTS exam_paper_question;
CREATE TABLE exam_paper_question (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  paper_id     BIGINT      NOT NULL,
  question_id  BIGINT      NOT NULL,
  seq_no       INT         NOT NULL DEFAULT 1,
  score        DECIMAL(5,1) NOT NULL DEFAULT 5,
  PRIMARY KEY (id),
  UNIQUE KEY uk_paper_question (paper_id, question_id)
) ENGINE=InnoDB COMMENT='试卷题目关联表';

DROP TABLE IF EXISTS exam_record;
CREATE TABLE exam_record (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  paper_id         BIGINT       NOT NULL,
  user_id          BIGINT       NOT NULL,
  plan_id          BIGINT       NULL                  COMMENT '关联培训计划，用于前后测对比',
  exam_stage       VARCHAR(16)  NOT NULL DEFAULT 'POST'
                   COMMENT '阶段：PRE培训前测/POST培训后测',
  objective_score  DECIMAL(6,1) NULL                  COMMENT '客观题得分（自动判分）',
  subjective_score DECIMAL(6,1) NULL                  COMMENT '主观题得分',
  total_score      DECIMAL(6,1) NULL,
  passed           TINYINT      NULL                  COMMENT '是否达标',
  submit_time      DATETIME     NULL,
  ai_comment       TEXT         NULL                  COMMENT 'AI评语（主观题辅助评分与评语）',
  create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_paper (user_id, paper_id),
  KEY idx_plan (plan_id)
) ENGINE=InnoDB COMMENT='考试记录表';

DROP TABLE IF EXISTS exam_answer;
CREATE TABLE exam_answer (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  record_id    BIGINT      NOT NULL,
  question_id  BIGINT      NOT NULL,
  user_answer  TEXT        NULL,
  is_correct   TINYINT     NULL                      COMMENT '客观题是否正确',
  score        DECIMAL(5,1) NULL,
  ai_score     DECIMAL(5,1) NULL                     COMMENT '大模型建议分（主观题）',
  manual_score DECIMAL(5,1) NULL                     COMMENT '人工复核分（用于一致性实验）',
  ai_reason    VARCHAR(500) NULL                     COMMENT '评分理由',
  create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_record (record_id),
  KEY idx_question (question_id)
) ENGINE=InnoDB COMMENT='答题明细表（主观题评分一致性实验的数据来源）';

DROP TABLE IF EXISTS answer_sheet;
CREATE TABLE answer_sheet (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  paper_id     BIGINT      NOT NULL                  COMMENT '试卷ID',
  user_id      BIGINT      NOT NULL                  COMMENT '考生',
  status       VARCHAR(16) NOT NULL DEFAULT 'ONGOING' COMMENT 'ONGOING作答中/SUBMITTED已交卷',
  start_time   DATETIME    NULL,
  submit_time  DATETIME    NULL,
  create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_paper_user (paper_id, user_id)
) ENGINE=InnoDB COMMENT='答题卡（记录作答中状态，支撑中断续答）';

-- =====================================================================
-- 5. AI 模块（RAG / Prompt 管理 / 生成留痕 / 效果诊断）
-- =====================================================================

DROP TABLE IF EXISTS kb_doc;
CREATE TABLE kb_doc (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  doc_name     VARCHAR(255) NOT NULL                 COMMENT '文档名称',
  doc_type     VARCHAR(32)  NOT NULL DEFAULT 'OTHER'
               COMMENT '类型：SKILL_STANDARD技能标准/COURSE_OUTLINE课程大纲/POLICY培训制度/POST_DESC岗位说明',
  source       VARCHAR(255) NULL                     COMMENT '来源说明（如《国家职业技能标准》），用于方案引用溯源',
  file_path    VARCHAR(255) NULL                     COMMENT '落库文件路径或原文位置',
  chunk_count  INT          NOT NULL DEFAULT 0       COMMENT '切分片段数',
  status       VARCHAR(16)  NOT NULL DEFAULT 'READY' COMMENT '索引状态：PENDING/PROCESSING/READY/FAILED',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_type (doc_type)
) ENGINE=InnoDB COMMENT='知识库文档表';

DROP TABLE IF EXISTS kb_chunk;
CREATE TABLE kb_chunk (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  doc_id      BIGINT       NOT NULL,
  chunk_index INT          NOT NULL DEFAULT 0        COMMENT '片段序号',
  content     TEXT         NOT NULL                  COMMENT '片段正文',
  token_count INT          NULL                      COMMENT '近似字数',
  embedding   LONGTEXT     NULL
              COMMENT '向量（JSON 数组）。千级片段下入库后加载至内存做余弦相似度计算，无需向量数据库',
  vec_model   VARCHAR(64)  NULL                      COMMENT '使用的嵌入模型名称',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_doc (doc_id),
  FULLTEXT KEY ft_content (content)                  COMMENT '关键词检索兜底'
) ENGINE=InnoDB COMMENT='知识库片段表（RAG 检索对象）';

DROP TABLE IF EXISTS ai_prompt_template;
CREATE TABLE ai_prompt_template (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  template_code VARCHAR(64)  NOT NULL                COMMENT '模板编码',
  template_name VARCHAR(128) NOT NULL,
  scene         VARCHAR(32)  NOT NULL
                COMMENT '场景：PLAN_GEN方案生成/EFFECT_DIAG效果诊断/RETRAIN补训方案/QA知识问答/PAPER_GEN组卷/JUDGE主观题评分',
  content       TEXT         NOT NULL                COMMENT 'Prompt 模板正文，用 {{变量}} 占位',
  input_schema  JSON         NULL                    COMMENT '期望输入的变量说明与校验规则',
  output_schema JSON         NULL                    COMMENT '期望输出的 JSON 结构，用于校验',
  model         VARCHAR(64)  NOT NULL DEFAULT 'deepseek-chat'
                COMMENT '使用的模型名称',
  temperature   DECIMAL(3,2) NOT NULL DEFAULT 0.30,
  max_tokens    INT          NULL,
  version       INT          NOT NULL DEFAULT 1      COMMENT '版本号，便于版本对比实验',
  status        TINYINT      NOT NULL DEFAULT 1,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code_version (template_code, version),
  KEY idx_scene (scene)
) ENGINE=InnoDB COMMENT='Prompt 模板表';

DROP TABLE IF EXISTS ai_gen_record;
CREATE TABLE ai_gen_record (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  biz_type           VARCHAR(32)  NOT NULL
                     COMMENT '业务类型：PLAN_GEN/EFFECT_DIAG/RETRAIN/QA/PAPER_GEN/JUDGE',
  biz_id             BIGINT       NULL               COMMENT '关联业务ID（如计划ID、考试记录ID）',
  user_id            BIGINT       NULL               COMMENT '触发用户',
  template_id        BIGINT       NULL               COMMENT '使用的 Prompt 模板',
  input_snapshot     JSON         NULL               COMMENT '输入快照（差距表等），保证可复现',
  retrieved_chunks   JSON         NULL               COMMENT '检索命中的知识片段ID与相似度，用于有/无RAG对比',
  output_content     LONGTEXT     NULL               COMMENT '模型原始输出',
  parsed_output      JSON         NULL               COMMENT '解析校验后的结构化结果',
  model              VARCHAR(64)  NULL,
  prompt_tokens      INT          NULL,
  completion_tokens  INT          NULL,
  cost_ms            INT          NULL               COMMENT '耗时（毫秒），性能指标',
  attempt_count      INT          NOT NULL DEFAULT 1 COMMENT '尝试次数（含重试）',
  status             VARCHAR(24)  NOT NULL DEFAULT 'SUCCESS'
                     COMMENT '状态：SUCCESS成功/SCHEMA_FAILED结构校验失败/RETRIED重试/FALLBACK降级为规则方案/API_ERROR接口异常',
  adopt_status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                     COMMENT '人工采纳情况：PENDING待确认/ADOPTED采纳/MODIFIED修改后采纳/REJECTED驳回',
  reject_reason      VARCHAR(500) NULL,
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_biz (biz_type, biz_id),
  KEY idx_status (status),
  KEY idx_create (create_time)
) ENGINE=InnoDB COMMENT='AI 生成记录表（结构合规率、采纳率、耗时等实验指标的数据来源）';

DROP TABLE IF EXISTS ai_qa_log;
CREATE TABLE ai_qa_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  user_id       BIGINT       NULL,
  question      VARCHAR(1000) NOT NULL,
  answer        TEXT         NULL,
  ref_chunks    JSON         NULL                    COMMENT '引用的知识片段ID与相似度',
  hit_score     DECIMAL(5,4) NULL                    COMMENT '最高相似度，用于检索质量分析',
  cost_ms       INT          NULL,
  feedback      TINYINT      NULL                    COMMENT '用户反馈：1有帮助 0无帮助，用于答案准确率统计',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_create (create_time)
) ENGINE=InnoDB COMMENT='知识问答日志表（检索命中率与答案质量统计）';

DROP TABLE IF EXISTS effect_eval;
CREATE TABLE effect_eval (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  plan_id        BIGINT       NOT NULL,
  user_id        BIGINT       NOT NULL,
  pre_score      DECIMAL(6,1) NULL                   COMMENT '培训前测成绩',
  post_score     DECIMAL(6,1) NULL                   COMMENT '培训后测成绩',
  score_improve  DECIMAL(6,1) NULL                   COMMENT '成绩提升值',
  gap_before     JSON         NULL                   COMMENT '培训前技能差距快照',
  gap_after      JSON         NULL                   COMMENT '培训后技能差距快照',
  gap_reduce     DECIMAL(6,2) NULL                   COMMENT '差距缩小率（%）',
  finish_rate    DECIMAL(5,2) NULL                   COMMENT '计划完成率（%）',
  diagnosis      TEXT         NULL                   COMMENT 'AI 效果诊断结论',
  retrain_plan   TEXT         NULL                   COMMENT 'AI 生成的补训方案',
  eval_result    VARCHAR(16)  NULL                   COMMENT '结论：EXCELLENT优秀/PASS达标/FAIL未达标',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_plan_user (plan_id, user_id)
) ENGINE=InnoDB COMMENT='培训效果评测表（闭环结果）';
