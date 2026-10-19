# AI 培训计划管理系统

基于 Spring Boot 3 + Vue 3 的企业培训管理平台，打通「岗位技能建模 → 技能差距分析 → 培训需求与计划 → AI 个性化方案 → 实施跟踪 → 考核评估」全链路，为培训决策提供数据支撑。

## 功能特性

| 模块 | 能力 |
|---|---|
| 用户与权限 | 三角色 RBAC（管理员 / 讲师 / 员工）、Sa-Token 鉴权、菜单与接口级权限控制 |
| 技能建模 | 岗位-技能矩阵、技能等级标准、加权差距量化，雷达图直观呈现能力短板 |
| 培训需求与计划 | 需求提报与审批、计划编制、状态流转（待审批 → 已通过 → 执行中 → 已完成） |
| AI 方案生成 | 规则引擎自动排期 + 大模型生成方案说明，JSON Schema 校验、失败自动降级 |
| 实施与跟踪 | 课程排期、进度上报、拖期预警、完成率统计 |
| 考核与评测 | 自动判分、前后测对比、培训效果诊断、补训建议生成 |

## 技术栈

**后端**：Spring Boot 3.3.5 · Java 21 · MyBatis-Plus · Sa-Token · Knife4j · MySQL 8.0

**前端**：Vue 3 · Vite · TypeScript · Element Plus · Pinia · ECharts

**数据库**：MySQL 8.0，25 张表

## 快速开始

### 1. 环境要求

| 依赖 | 版本 |
|---|---|
| JDK | 21 |
| MySQL | 8.0 |
| Node.js | 20.19+ 或 22.12+ |

### 2. 初始化数据库

```bash
cd work/app/server/sql

mysql -uroot -p -e "CREATE DATABASE ai_training DEFAULT CHARACTER SET utf8mb4;"
mysql -uroot -p ai_training < 01-schema.sql
mysql -uroot -p ai_training < 02-seed.sql
```

### 3. 配置数据库密码

**方式一（推荐）**：复制 `work/app/server/application-local.yml.example` 为 `application-local.yml`，填入你的 MySQL 密码。该文件已被 `.gitignore` 排除，不会误提交。

**方式二**：设置环境变量

```bash
# Windows (cmd)
set DB_PASSWORD=你的密码

# macOS / Linux
export DB_PASSWORD=你的密码
```

### 4. 启动后端

```bash
cd work/app/server

# 开发模式（首次会自动下载依赖）
mvn spring-boot:run

# 或先打包再运行
mvn package -DskipTests
java -jar target/ai-training-server-1.0.0.jar
```

启动成功后访问接口文档：http://localhost:8000/api/doc.html

### 5. 启动前端

```bash
cd work/app/web
npm install
npm run dev
```

打开 http://localhost:3000

## 默认账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 管理员 | `admin` | `123456` |
| 讲师 | `lecturer01` | `123456` |
| 员工 | 见 `02-seed.sql` 中 `sys_user` 表 | `123456` |

> 演示账号仅用于本地体验，部署到公网前请务必修改密码。

## AI 能力说明

系统对接 DeepSeek 大模型生成培训方案说明，**API Key 是可选的**：

- 未配置 Key 时，自动降级为纯规则引擎，方案排期、评分、报表等全部功能仍可正常使用
- 配置方式：设置环境变量 `DEEPSEEK_API_KEY`，或在 `application-local.yml` 中填写 `ai.llm.api-key`

```bash
set DEEPSEEK_API_KEY=sk-xxxxxxxx
```

> 没有 Key 完全不影响功能演示与答辩，AI 只影响方案中的文字说明部分。

## 目录结构

```
work/app/
├── server/                     后端
│   ├── src/main/java/com/aitraining/
│   │   ├── common/             通用配置、异常处理、结果封装
│   │   └── module/
│   │       ├── auth/           认证与权限
│   │       ├── system/         用户、部门、岗位、角色
│   │       ├── skill/          技能与岗位技能矩阵
│   │       ├── train/          需求、计划、课程、进度
│   │       ├── exam/           试卷、答题、判分
│   │       └── ai/             LLM 调用、方案生成、知识库
│   ├── src/main/resources/application.yml
│   ├── application-local.yml.example   本地配置模板
│   └── sql/                    建表与种子数据
└── web/                        前端
    └── src/
        ├── api/                接口封装
        ├── views/              页面（admin/ai/exam/home/me/progress/skill/train）
        ├── router/             路由与权限守卫
        └── stores/             状态管理
```

## 配置项说明

| 配置 | 位置 | 说明 |
|---|---|---|
| 数据库连接 | `application.yml` | 地址、库名、用户名，密码走环境变量或 local 配置 |
| 服务端口 | `application.yml` `server.port` | 默认 8000，接口前缀 `/api` |
| 大模型 | `application.yml` `ai.llm` | base-url、model、温度、超时、重试 |
| 检索增强 | `application.yml` `ai.rag` | top-k、最低相关度、上下文长度上限 |

## License

[MIT](LICENSE)
