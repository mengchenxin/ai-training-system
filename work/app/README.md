# 基于 Spring Boot 的 AI 培训计划管理系统

面向企业岗位技能提升场景的培训管理平台，把「技能差距量化 → AI 生成个性化方案 → 计划执行 → 考核 → 效果诊断 → 补训」串成可闭环的数据链路。

## 一、技术栈

| 层次 | 选型 | 说明 |
| --- | --- | --- |
| 后端 | Spring Boot 3.3.5 / Java 21 | 分层架构，RESTful 接口 |
| 持久层 | MyBatis-Plus 3.5.7 | 分页插件、条件构造器、代码量少 |
| 权限 | Sa-Token 1.38.0 | JWT + Session，三角色 RBAC 与接口级鉴权 |
| 数据库 | MySQL 8.0 | 25 张表，utf8mb4 |
| 接口文档 | Knife4j 4.5.0 | 启动后访问 `/api/doc.html` |
| 前端 | Vue 3 + Vite + TypeScript | Composition API + `<script setup>` |
| UI | Element Plus 2.14 + ECharts | 表格/表单/雷达图/柱状图/饼图 |
| 状态管理 | Pinia | 登录态与角色权限 |
| AI | DeepSeek（OpenAI 兼容协议） | 规则引擎 + 大模型混合，未配置 Key 时自动降级 |
| 检索增强 | 自研 BM25 | 中文 2-gram 倒排索引，无外部向量库依赖 |

## 二、快速开始

### 1. 环境要求

- JDK 21
- Maven 3.9+
- MySQL 8.0
- Node.js 20.19+ 或 22.12+

### 2. 初始化数据库

```bash
mysql -uroot -p < server/sql/01-schema.sql
mysql -uroot -p < server/sql/02-seed.sql
```

`01-schema.sql` 建库建表，`02-seed.sql` 导入演示数据（可重复执行，开头会 TRUNCATE）。
种子数据规模：5 个部门、20 个技能点、8 个岗位、64 个用户、377 条员工技能档案、30 门课程、80 道题、45 条知识库片段。

如需重新生成种子数据，可用内置生成器（固定随机种子，结果可复现）：

```bash
python server/sql/gen_seed.py > server/sql/02-seed.sql
```

### 3. 配置

修改 `server/src/main/resources/application.yml` 中的数据源：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/ai_training?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 你的密码
```

### 4. 启动

**MySQL**

本机使用**独立实例**（数据目录 `D:\develop\mysql-data\ai-training`，配置文件 `D:\develop\mysql-data\conf\ai-training.cnf`），与系统原有的 `MySQL80` 服务互相隔离：

```bash
start-mysql.bat      # 3306 已在监听时直接退出，否则启动并等待就绪
```

> 两者共用 3306 端口，**不要同时启动** `MySQL80` 服务，否则会端口冲突。

**一键启动**（依次检查 MySQL → 后端 → 前端）

```bash
start-all.bat
```

**分别启动**

后端：

```bash
cd server
mvn clean package -DskipTests
java -jar target/ai-training-server-1.0.0.jar --server.port=8000
```

前端：

```bash
cd web
npm install        # 或 pnpm install
npm run dev
```

访问 <http://127.0.0.1:3000>。前端通过 Vite 代理把 `/api` 转发到 `http://127.0.0.1:8000`，因此后端 `context-path` 保持 `/api` 不变。

开发阶段也可以不打包，直接用 Maven 插件启动：

```bash
cd server
mvn spring-boot:run
```

> 若启动时报 `ClassNotFoundException: com.aitraining.AiTrainingApplication`，原因与解决办法见[第十节](#十常见问题mvn-spring-bootrun-报找不到主类)。

## 三、演示账号

密码统一为 `123456`。

| 账号 | 角色 | 可做什么 |
| --- | --- | --- |
| `admin` | 管理员 | 用户/角色/部门/岗位维护，全局数据 |
| `lecturer01` | 讲师 | 技能模型、培训计划、组卷出题、进度看板、效果诊断 |
| `emp001` | 员工 | 我的技能差距、我的培训任务、进度上报、在线考试、知识问答 |

> 讲师角色在本系统中兼主管职责：建计划、审方案、出题、看进度。

## 四、功能模块

### 1. 用户与权限管理

三角色 RBAC。Sa-Token 在登录时把角色码写入 Session，`StpInterface` 按角色返回权限点，接口用 `LoginHelper.requireManager()` / `requireAdmin()` / `requireSelfOrManager(userId)` 做细粒度校验。

### 2. 岗位技能模型与技能差距分析

技能点清单（名称、类别、等级描述）→ 岗位技能要求（目标等级 + 权重）→ 员工技能档案（当前等级 + 评定来源）。

差距量化公式：

```
gap         = max(0, targetLevel - currentLevel)
weightedGap = gap × weight
priority    = weightedGap / maxWeightedGap × 100
matchRate   = 1 - ΣweightedGap / Σ(targetLevel × weight)
```

排序规则 `weightedGap desc → weight desc → skillId asc`，输出差距明细表、雷达图（当前 vs 目标）、群体短板热力图。

### 3. AI 个性化培训方案生成

**混合策略**，职责分离：

| 环节 | 承担者 | 原因 |
| --- | --- | --- |
| 召回与排期 | 规则引擎 | 确定性，可预算约束，不会排出不存在的课 |
| 方案说明与选课理由 | 大模型 | 自然语言表达，模型无法保证课程存在性 |
| 结构校验 | JSON Schema 校验器 | 课程必须来自候选集、无重复、学时不超预算 |
| 降级 | 纯规则方案 | 无 Key / 调用失败 / 校验不过时仍然可用 |

规则引擎按 `weightedGap` 降序选课，每技能挑「课程等级最接近目标等级」的一门；`gap ≥ 2` 且有多门候选时安排「基础 + 进阶」两门；受学时预算约束。

大模型返回后逐项校验，不通过则重试，仍不通过按 `SCHEMA_FAILED` 记录并降级。每次生成写入 `ai_gen_record`（状态、tokens、耗时、重试次数、RAG 命中、采纳结果），指标页据此统计，直接作为论文实验数据。

### 4. 培训实施与进度跟踪

计划状态机（白名单流转，非法流转被拒绝）：

```
DRAFT → PENDING → PUBLISHED → RUNNING → FINISHED
   ↓        ↓          ↓           ↓
 CANCELED CANCELED   CANCELED    CANCELED
```

发布时按「目标人群 × 计划课程」批量生成进度记录。员工上报进度后自动推进计划状态（全完成 → FINISHED）。拖期风险判定：完成率 < 60% 且已过计划周期 70%。

### 5. 考核与效果测评

- **自动组卷**：按技能范围 + 难度分布抽题，总分均分、尾差补最后一题
- **自动判分**：单选/判断归一化后集合比较（忽略大小写与分隔符）；简答题优先大模型评分，无 Key 时降级为「参考答案二元组覆盖率」规则评分，评分理由入库可复核
- **效果诊断**：前测/后测对比 → 成绩回写技能档案（≥85 提 2 级、≥60 提 1 级，只升不降且不超目标）→ 重新量化差距算缩小率 → 规则判定 EXCELLENT / PASS / FAIL → 未达标自动生成补训方案

## 五、AI 模块配置

### 接入 DeepSeek

```bash
# Windows PowerShell
$env:DEEPSEEK_API_KEY = "sk-xxxxxxxx"

# Git Bash / Linux / macOS
export DEEPSEEK_API_KEY="sk-xxxxxxxx"
```

配置后启动，`GET /api/ai/status` 返回 `llmAvailable: true`，方案生成方式从 `AI_RULE` 变为 `AI_HYBRID`。

**未配置 Key 也能完整演示全部功能** —— 这是刻意设计：规则引擎独立产出完整方案，大模型只负责把方案说明写得更像人话。答辩时可以先演示降级模式，再配 Key 演示混合模式，形成对照实验。

### RAG 知识库

启动时全量加载 `kb_doc` / `kb_chunk` 到内存倒排索引（BM25，K1=1.5、B=0.75），中文按 2-gram + 单字切分，得分归一化到 0–1。无需向量数据库或外部 Embedding 服务。

在「AI 智能 → 知识库」页面新增文档会自动按句号切分 300 字/片段并重建索引。

## 六、目录结构

```
app/
├── server/                      后端
│   ├── sql/
│   │   ├── 01-schema.sql        建表脚本（25 张表）
│   │   ├── 02-seed.sql          种子数据
│   │   └── gen_seed.py          种子数据生成器
│   ├── tools/
│   │   └── e2e_closed_loop.py   端到端闭环测试脚本
│   └── src/main/java/com/aitraining/
│       ├── common/              统一响应、分页、异常处理
│       ├── config/              MyBatis-Plus、Sa-Token、Knife4j、Jackson
│       ├── security/            登录上下文、权限点
│       └── module/
│           ├── auth/            登录登出
│           ├── system/          用户、部门、岗位、角色
│           ├── skill/           技能点、岗位模型、差距分析
│           ├── train/           课程、需求、计划、进度
│           ├── exam/            题库、组卷、判分
│           └── ai/              方案生成、RAG、问答、评分、效果诊断
└── web/                         前端（Vue 3 + Vite + TS）
    ├── src/                     仅 31 个源码文件 + 静态资源，无框架模板冗余
    │   ├── api/                 接口封装（request.ts 拦截器 + index.ts 接口定义）
    │   ├── assets/              图标与 logo
    │   ├── lang/                语言包
    │   ├── layout/              框架布局与分组菜单
    │   ├── router/              23 条路由与角色守卫
    │   ├── stores/              Pinia 登录态
    │   ├── styles/              全局样式与 Element Plus 变量
    │   └── views/               23 个业务页面（auth/home/admin/skill/train/progress/exam/ai/me）
    ├── tools/                   构建校验脚本（可达性分析、逐页面编译检查）
    └── .backup-src-20261017/    清理前完整备份，含 _removed/ 下已移出的模板遗留文件
```

## 七、端到端验证

```bash
python server/tools/e2e_closed_loop.py
```

脚本会跑完整条业务链路并输出断言结果（25 项）：

技能差距 → AI 方案 → 采纳 → 建计划 → 审核发布 → 进度上报 → 自动组卷 → 前测/后测 → 效果诊断 → 技能档案回写 → 指标统计 → 知识问答

实测结果（无大模型 Key 的纯规则模式）：

```
员工 emp001 达标率 54.39%，6 项差距，加权差距 10.40
AI 方案 AI_RULE，5 门课 40 学时，RAG 引用 5 条
前测 0 分 → 后测 100 分（客观 77.7 + 主观 22.3）
效果诊断 EXCELLENT，差距缩小 61.63%
技能档案回写后达标率 85.53%，加权差距 3.30
```

## 八、接口文档

启动后端后访问 <http://127.0.0.1:8000/api/doc.html>。

首次调用需先登录拿 token，在 Knife4j 的「全局参数」中加上：

```
Authorization: Bearer <token>
```

## 九、前端代码清理说明

前端最初基于 vue3-element-admin 模板搭建，模板自带大量与本项目无关的页面与组件（工作流 BPMN、代码生成器、动态表单、demo 页、系统管理二级页等）。完成重写后，这些文件已无人引用，成为死代码，并且会让类型检查报错、依赖清单臃肿。

清理依据是**从入口做可达性分析**，不靠人工猜测：

```
main.ts → App.vue → router/index.ts → views/*.vue → api/ stores/ layout/
```

可达集合 31 个文件，其余 249 个均不可达。

| 项目 | 清理前 | 清理后 |
| --- | --- | --- |
| `src/` 源码文件 | 280 | 31 |
| `src/` 目录数 | 128 | 21 |
| `src/` 文件总数（含静态资源） | 356 | 97 |
| `package.json` dependencies | 30 | 7 |
| `vue-tsc` 类型错误 | 26 | 0 |

`dependencies` 只保留源码真实引用的 7 个：`vue`、`vue-router`、`pinia`、`element-plus`、`@element-plus/icons-vue`、`axios`、`echarts`。移出的 23 个（`bpmn-js`、`vxe-table`、`@wangeditor-next/*`、`@form-create/*`、`codemirror*`、`exceljs`、`vue-i18n`、`lodash-es` 等）均已确认无任何 import。

**清理后必须仍然满足的三项验证**（全部通过）：

```bash
cd web
npx vue-tsc --noEmit                       # 类型检查：0 错误
npx vite build                             # 生产构建：2410 modules，成功
bash tools/check-pages.sh                  # 逐页面 dev 编译：25/25 通过
```

**还原方式**：清理采用「移出」而非「删除」，文件完整保存在 `.backup-src-20261017/`（原始全量）与其中的 `_removed/`（已移出的 180 个 + 模板大屏资源）。需要恢复任意文件直接拷回 `src/` 对应位置即可。`package.json` 的原始版本备份为 `package.json.bak`。

注：若在受严格删除策略的沙箱环境中执行构建，`vite build` 清空旧 `dist/` 时可能被拦截（提示 `SAFE_DELETE_BULK_CONFIRM_REQUIRED`）。这只是环境限制，不影响源码正确性；在普通终端中直接 `npm run build` 即可。

### 前端根目录与静态资源的二次清理

`src/` 清理之后，`web/` 根目录和静态资源里仍有一批模板残留，逐项核实引用数后一并移入 `.backup-src-20261017/_removed/`：

| 移出项 | 判断依据 |
| --- | --- |
| `.env.development` / `.env.production` | `src/` 内 **0 处** `import.meta.env` 引用；且原值为模板作者的标题与线上接口 `api.youlai.tech` |
| `mock/`（15 个文件） | 对应插件 `vite-plugin-mock-dev-server` 已移除，不会被加载 |
| `.husky/`（含 `pre-commit`） | 配合 `core.hooksPath` 会在 `git commit` 时执行 lint-staged，可能阻断提交；已同时 `git config --unset core.hooksPath` |
| `pnpm-lock.yaml` / `pnpm-workspace.yaml` | 与精简后的 `package.json` 已不一致，且项目用 npm |
| `uno.config.ts` | 对应 `unocss` 已移除，`vite.config.ts` 未引用 |
| `README.md` / `README.en-US.md` / `deploy*.mjs` | 模板自带文档与腾讯云部署脚本 |
| `public/images`（8.9M） | 模板宣传截图与微信二维码，引用数 **0** |
| `public/map/china.json` | 地图组件数据，引用数 **0** |
| `src/assets/icons`（66 个 svg）、`src/assets/images/logo.png` | 图标统一用 Element Plus，引用数 **0** |

同时修正了 `package.json` 中会实际影响使用的两处：

- **删除 `"preinstall": "npx only-allow pnpm"`** —— 否则按 README 执行 `npm install` 会被直接拒绝；
- `name` / `description` / `version` / `author` / `repository` 由模板值改为本项目值（原为 `vue3-element-admin`、`有来开源组织`）。

清理后 `dist/` 由 12M 降至 **3.2M**（102 个文件），`vue-tsc` 仍为 0 错误，构建 2410 modules 通过。

> 另注：`web/.git` 是模板仓库克隆带下来的（remote 原本指向 `youlaitech/vue3-element-admin`）。已移除该 remote 以免误推到他人仓库。若要提交毕设代码，建议在 `work/app` 根目录重新 `git init`，把 `server/` 与 `web/` 一并纳入管理。

## 十、常见问题：`mvn spring-boot:run` 报找不到主类

**现象**

```
[INFO] --- spring-boot:3.3.5:run (default-cli) @ ai-training-server ---
[INFO] Attaching agents: []
错误: 找不到或无法加载主类 com.aitraining.AiTrainingApplication
原因: java.lang.ClassNotFoundException: com.aitraining.AiTrainingApplication
[INFO] BUILD FAILURE
```

但 `target/classes` 里 `AiTrainingApplication.class` 明明存在，`mvn package` + `java -jar` 也一切正常。

**原因**

这是 Spring Boot 的一个已知缺陷（[spring-boot#43051](https://github.com/spring-projects/spring-boot/issues/43051)），**从 3.3.0 开始引入，3.3.5 仍未修复，3.2.x 及更早版本不受影响**。

3.3.0 起，`spring-boot:run` 为了绕开 Windows 命令行 32K 的长度限制，把 classpath 写进一个临时 **argfile**，再用 `java @argfile` 的方式传给子进程：

```
Java arguments: -XX:TieredStopAtLevel=1 -cp @C:\Users\...\Temp\spring-boot-xxxx.argfile com.aitraining.AiTrainingApplication
```

问题在于：argfile **以 UTF-8 写入**，而 JVM 读取 `@argfile` 时使用的是系统原生编码（中文 Windows 下 `sun.jnu.encoding = GBK`）。路径是纯英文时两种编码字节完全一致，看不出差别；一旦路径含中文，字节被按 GBK 解释成别的字符，classpath 整体失效，于是「找不到主类」。

本项目同时命中两处中文路径：

- 项目路径 `C:\Users\梦萦\Documents\...`
- 本地仓库 `C:\Users\梦萦\.m2\repository`（依赖 jar 全在这里）

**已做的处理**

`server/pom.xml` 中把 `spring-boot-maven-plugin` 固定为 **3.2.11**（不继承父 POM 的 3.3.5）。3.2.x 的插件不生成 argfile，因此不受影响；而插件版本与应用框架版本相互独立，3.2.11 的插件启动 Spring Boot 3.3.5 的应用完全正常。已实测：

| 命令 | 结果 |
| --- | --- |
| `mvn spring-boot:run`（原中文路径） | 启动成功，Tomcat 8000 / RAG 索引 45 条 |
| `mvn package -DskipTests` | 成功，产出 40M fat jar |
| `java -jar target/ai-training-server-1.0.0.jar` | 正常 |

**其他可选方案**（供换机或升级时参考）

| 方案 | 做法 | 代价 |
| --- | --- | --- |
| 不用插件启动 | `mvn package` 后 `java -jar target/xxx.jar --server.port=8000` | 无，本项目 `start-backend.bat` 即如此 |
| 系统级修复 | 控制面板 → 区域 → 管理 → 更改系统区域设置 → 勾选「Beta: 使用 Unicode UTF-8」→ 重启 | 一劳永逸，但会影响部分老旧国产软件的编码 |
| 换 ASCII 路径 | 项目与本地仓库都放到纯英文路径（如 `D:\bi-app`），可用目录联接 `mklink /J` 映射 | 需重新配置 IDE 与 Maven 仓库位置 |
| 升级框架 | Spring Boot 升到已修复版本（需逐个验证 MyBatis-Plus / Sa-Token / Knife4j 兼容性） | 风险最高，不建议为这个问题单独升级 |

> 注意：仅把**项目**移到英文路径并不能解决，必须让 classpath 里的每一项（含 `.m2` 仓库）都不含中文。

