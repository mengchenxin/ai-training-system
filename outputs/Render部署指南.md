# 部署到 Render 指南（给老师看用）

> 目标：老师打开一个链接就能自己点、自己看，不需要你在旁边讲。
> 架构：前端静态站（Render）+ 后端 Docker（Render）+ MySQL（TiDB Cloud 免费版）
>
> 预计耗时：**首次 40–60 分钟**，其中 TiDB 建库和导入数据占大半。

---

## 为什么数据库要放 TiDB

Render 只提供 Postgres 和 Redis，**不提供托管 MySQL**。你的库是 MySQL 8，所以必须找外部 MySQL。
选 TiDB Cloud Serverless 的理由：免费 5GB、协议兼容 MySQL、不用改一行代码、控制台自带 SQL 编辑器。

代码层面唯一要改的已经改好了：`sql/99-tidb-schema.sql` 是 TiDB 专用 DDL，
把 TiDB 不支持的 `FULLTEXT` 索引去掉了（你的检索是应用层自实现 BM25，不依赖数据库全文索引，所以没影响）。

---

## 第一步：TiDB 建库并导入数据

### 1.1 建集群

1. 打开 https://tidbcloud.com，用 GitHub 账号登录
2. 左侧 **Clusters** → **Create Cluster** → 选 **Serverless**
3. 区域选 **Singapore (ap-southeast-1)**（离国内最近）
4. 集群名随意，创建后等 1–2 分钟变成 **Active**

### 1.2 拿连接信息

集群页面点 **Connect**，弹窗里会给出：

```
Host:     gateway01.ap-southeast-1.prod.aws.tidbcloud.com
Port:     4000
User:     3xxxxxx.root
Password: 点击 Generate 生成（只显示一次，务必复制保存）
```

> ⚠️ 密码只显示一次。生成后立刻存到记事本。

### 1.3 导入数据

TiDB Serverless **必须走 TLS**，连接时要加 `--ssl-mode=VERIFY_IDENTITY`。

在你电脑的 cmd 里（MySQL 客户端路径按你实际安装位置）：

```bat
cd /d C:\Users\梦萦\Documents\Codex\2026-10-14\bi\work\app\server\sql

"D:\develop\MySQL\MySQL Server 8.0\bin\mysql.exe" ^
  --ssl-mode=VERIFY_IDENTITY ^
  -h gateway01.ap-southeast-1.prod.aws.tidbcloud.com ^
  -P 4000 ^
  -u "3xxxxxx.root" ^
  -p ^
  < 99-tidb-schema.sql
```

回车后输入密码。**这一步会建库 `ai_training` 和 25 张表。**

然后导入种子数据：

```bat
"D:\develop\MySQL\MySQL Server 8.0\bin\mysql.exe" ^
  --ssl-mode=VERIFY_IDENTITY ^
  -h gateway01.ap-southeast-1.prod.aws.tidbcloud.com ^
  -P 4000 ^
  -u "3xxxxxx.root" ^
  -p ^
  ai_training < 02-seed.sql
```

> 注意第二条命令多了 `ai_training`，是指定库名。

### 1.4 验证

```bat
"D:\develop\MySQL\MySQL Server 8.0\bin\mysql.exe" --ssl-mode=VERIFY_IDENTITY ^
  -h <host> -P 4000 -u "<user>" -p -e "SELECT COUNT(*) FROM ai_training.sys_user;"
```

应该返回 **64** 左右（用户数）。如果报 `Unknown database`，说明第一条没导成功。

### 1.5 补齐部门演示数据（可选，但强烈建议）

本地跑过 `tools/seed_dept_progress.py` 生成的部门数据**没有在 seed.sql 里**。
为了让老师看到六个部门都有完成率的看板，等后端部署好后，改一下脚本里的地址再跑一遍：

```python
BASE = "https://你的后端域名.onrender.com/api"
```

然后 `python tools/seed_dept_progress.py`。它会通过接口真实建计划、上报进度。

---

## 第二步：Render 部署

### 2.1 推送代码到 GitHub

`render.yaml`、`Dockerfile`、`.dockerignore`、`99-tidb-schema.sql` 是新文件，**必须先提交推送**，否则 Render 读不到。

```bat
cd /d C:\Users\梦萦\Documents\Codex\2026-10-14\bi
git add -A
git commit -m "feat: 增加 Render 部署配置与 TiDB 兼容 DDL"
git push
```

### 2.2 创建 Blueprint

1. 打开 https://render.com，用 GitHub 登录
2. 点 **New** → **Blueprint**
3. 选仓库 `mengchenxin/ai-training-system`
4. Render 会自动读取 `render.yaml`，显示两个服务：
   - `ai-training-server`（后端 Docker）
   - `ai-training-web`（前端静态站）
5. 点 **Apply**

### 2.3 填环境变量（关键步骤）

后端 `ai-training-server` 里有 4 个变量要手动填（`sync: false` 的都不会进代码仓库）：

| 变量名 | 填什么 |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://<host>:4000/ai_training?sslMode=VERIFY_IDENTITY&useSSL=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8` |
| `SPRING_DATASOURCE_USERNAME` | `3xxxxxx.root` |
| `SPRING_DATASOURCE_PASSWORD` | TiDB 生成的密码 |
| `DEEPSEEK_API_KEY` | 你的 DeepSeek key（不填则降级为纯规则方案） |

> `SPRING_DATASOURCE_URL` 是最容易写错的一个。注意三点：
> 1. 必须有 `sslMode=VERIFY_IDENTITY&useSSL=true`，否则 TiDB 拒绝连接
> 2. 端口是 **4000**，不是 3306
> 3. 不要漏掉 `/ai_training`

### 2.4 等后端构建

首次构建要跑 Maven 下载依赖，**约 6–10 分钟**。在 **Logs** 里能看到进度。

看到这行就成功了：

```
Started AiTrainingApplication in xx seconds
```

然后访问健康检查地址验证：

```
https://ai-training-server-xxxx.onrender.com/api/health
```

应该返回 `{"code":0,"msg":"success","data":{"status":"UP",...}}`

### 2.5 重新构建前端并推送（必须做，否则登录 404）

> **为什么前端不在 Render 上构建**：项目依赖是用 pnpm 装的，但仓库里没有 `pnpm-lock.yaml`。
> 云端用 `npm install` 会重新解析出不同版本，构建大概率失败。所以前端产物在本地构建后提交，
> Render 只做静态托管。**代价是以后改了前端，要重新跑一次这个脚本。**

后端域名拿到后，在 cmd 里执行：

```bat
cd /d C:\Users\梦萦\Documents\Codex\2026-10-14\bi\work\app\web
build-for-render.bat https://ai-training-server-xxxx.onrender.com
```

脚本会自动把 `VITE_API_BASE` 设成 `<你的域名>/api` 并构建。

构建完成后推送：

```bat
cd /d C:\Users\梦萦\Documents\Codex\2026-10-14\bi
git add -A
git commit -m "build: 更新前端产物并指向线上后端"
git push
```

Render 检测到仓库变化会自动重新发布静态站（这次是纯拷贝文件，**约 30 秒**）。

> ⚠️ 在你跑完这一步之前，前端页面能打开但**登录会 404**——因为 dist 里的 API 地址还是相对路径 `/api`，
> 请求打到了前端自己的域名。这是预期现象，不是配错了。

### 2.6 验证

打开 `https://ai-training-web-xxxx.onrender.com`：

1. 登录页会出现 **admin / 123456**，点标签自动填充
2. 点登录 → 如果实例在睡觉，等 30–60 秒，页面会提示"云端服务正在唤醒"
3. 进工作台，看数字是否和本地一致

---

## 第三步：防止实例休眠（保活）

Render 免费实例 **15 分钟没有请求就休眠**，下次访问冷启动 50 秒左右。
老师第一次点开就要等一分钟，体验很差。

### 方案 A：UptimeRobot（最简单）

1. https://uptimerobot.com 注册（免费 50 个监控）
2. Add New Monitor → HTTP(s)
3. URL 填 `https://ai-training-server-xxxx.onrender.com/api/health`
4. 间隔选 **10 分钟**
5. 保存

### 方案 B：GitHub Actions（不用注册新账号）

仓库里已放好 `.github/workflows/keepalive.yml`，只需加一个 secret：

1. GitHub 仓库 → **Settings** → **Secrets and variables** → **Actions**
2. **New repository secret**
   - Name: `BACKEND_URL`
   - Value: `https://ai-training-server-xxxx.onrender.com`
3. 推送 workflow 文件后生效

> ⚠️ GitHub Actions 的定时任务在 **60 天无提交后会自动停用**，且执行时间有 5–15 分钟延迟。
> 答辩前一周记得手动触发一次确认它还活着。

---

## 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 页面白屏 / 一直转圈 | 后端还在冷启动 | 等 60 秒；确认保活任务在跑 |
| 登录提示 401 / "登录状态已失效" | Sa-Token 用内存存 token，实例重启后全失效 | 重新登录即可，正常现象 |
| 登录后页面空白，控制台 404 | `VITE_API_BASE` 没填或漏了 `/api` | 回 2.5 步 |
| 后端日志 `Communications link failure` | TiDB 连接串缺 SSL 参数 | 回 2.3 步检查 `useSSL=true` |
| 后端日志 `Unknown database 'ai_training'` | DDL 没导入成功 | 回 1.3 步 |
| 建表报 `FULLTEXT index is not supported` | 用了 `01-schema.sql` 而不是 `99-tidb-schema.sql` | 用 99 那个文件 |
| 部署后 502 | 容器启动失败，多半是环境变量没填全 | 看 Logs 里第一段异常 |
| 内存 OOM 反复重启 | 免费实例只有 512MB | `render.yaml` 里已设 `MaxRAMPercentage=72` + SerialGC；还不够就把 `DB_POOL_SIZE` 改成 3 |

---

## 成本与限制（写进论文的"不足与展望"很合适）

| 项 | 免费额度 | 对本项目的影响 |
|---|---|---|
| Render Web Service | 512MB 内存，15 分钟休眠 | 冷启动 50 秒，演示体验受影响 |
| Render 构建时长 | 每月 500 分钟 | Maven 构建约 8 分钟/次，够用十几次 |
| Render 带宽 | 100GB/月 | 绰绰有余 |
| TiDB Serverless | 5GB 存储，免费 | 数据量不到 50MB |
| 国内访问速度 | 无 CDN 保障 | 首屏 2–5 秒，可能偶发超时 |

**这些正好可以写进论文的局限性一节**：受限于免费云资源，实例存在冷启动延迟；
生产环境应采用常驻实例 + CDN + 连接池预热。
