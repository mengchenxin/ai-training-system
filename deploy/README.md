# 线上部署手册

目标形态：一台 2C2G 的国内云服务器，用 Docker Compose 起三个容器 —— MySQL 8、Spring Boot 后端、Nginx 前端。
对外只暴露一个端口（默认 8080），前端静态文件与 `/api` 反代都走 Nginx，同源，不需要配跨域。

```
浏览器 ──> Nginx:8080 ──┬── /            静态文件（dist）
                        └── /api/*       反代 backend:8000（后端 context-path 就是 /api）
                                     │
                              Spring Boot ──> MySQL:3306（仅内网）
```

## 一、买服务器

| 项 | 建议 |
|---|---|
| 配置 | 2 核 2G 起，系统盘 40G+ |
| 系统 | Ubuntu 22.04 / 24.04（脚本按 apt 写的，最省事） |
| 带宽 | 按量或 3M 固定都够，纯文本页面 |
| 备案 | **不用备案也能用**：未备案封的是 80/443，本方案对外用 8080，IP:8080 直接访问 |
| 安全组 | 放行 8080（以及 22） |

腾讯云/阿里云学生认证通常有 ¥9–10/月 的 2C2G 套餐，年付约 ¥100。

## 二、服务器初始化（只做一次）

```bash
# 上传并执行
scp deploy/server-init.sh root@<服务器IP>:/root/
ssh root@<服务器IP> "bash /root/server-init.sh"
```

脚本做三件事：加 2G swap（2G 内存必须，否则 MySQL+JVM 会 OOM）、装 Docker 与 compose 插件、写国内镜像加速。

## 三、本地打包

```powershell
cd D:\Desktop\learn\graduate\bi
powershell -ExecutionPolicy Bypass -File deploy\build.ps1
```

产出 `bi\deploy-ai-training.tar.gz`（约 30–60MB，含 jar、dist、初始化 SQL）。脚本会先跑 `mvn package -DskipTests`，再 `vite build`，缺文件会直接报错。

> 为什么在本地构建而不是服务器上构建：本机 `node_modules` 是 pnpm 装的且**没有 lock 文件**，在服务器重新 `npm install` 会拉到不同版本，可能构建失败；2C2G 上跑 vite 也慢。本地出产物、服务器只跑容器，最稳。

## 四、上传并启动

```bash
scp deploy-ai-training.tar.gz root@<服务器IP>:/opt/
ssh root@<服务器IP>

cd /opt
tar -xzf deploy-ai-training.tar.gz      # 解出 /opt/deploy
cd /opt/deploy

cp .env.example .env
vi .env                                  # 必改 DB_PASSWORD；有 Key 就填 DEEPSEEK_API_KEY

docker compose up -d --build
docker compose ps
docker compose logs -f backend           # 看到 Started Application 即成功
```

首次启动会执行 `init/10-data.sql`（2.5MB），导入 25 张表与全部演示数据，约 30–60 秒。MySQL 健康检查通过后后端才启动。

访问：`http://<服务器IP>:8080`，账号 `admin` / `123456`。

## 五、日常运维

```bash
docker compose ps                 # 状态
docker compose logs -f backend    # 后端日志
docker compose restart backend    # 重启后端
docker compose down               # 停止（数据保留在卷里）
docker compose down -v            # 停止并清空数据库（下次启动重新导入初始数据）
docker stats                      # 看内存占用
```

更新版本：本地重新 `build.ps1` → 上传 → 服务器上 `tar -xzf` 覆盖 → `docker compose up -d --build`。数据库卷不动，数据不丢。

备份数据库：

```bash
docker exec ait-mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction ai_training' \
  | gzip > /opt/backup-$(date +%F).sql.gz
```

## 六、常见问题

**页面能开但接口 502**
后端还没起来或崩了。`docker compose logs backend` 看报错；内存不足时 JVM 会被 OOM kill，确认 swap 已生效（`free -h`）。

**MySQL 反复重启**
2G 内存被其他进程吃了。`docker stats` 看占用；确认 `mysql/my.cnf` 挂载成功（`docker exec ait-mysql cat /etc/mysql/conf.d/zz-custom.cnf`）。

**登录后大面积 401**
后端与前端不同源或反向代理没带 Host 头。检查 `nginx/nginx.conf` 里 `/api/` 段的 `proxy_set_header Host $host;`。

**AI 功能"没反应"、方案生成很快返回**
多半是没配 `DEEPSEEK_API_KEY`，系统进了纯规则降级模式。`/ai/metrics` 页面能看到当前模式；配好 Key 后 `docker compose restart backend`。

**想改密码后重新初始化**
`DB_PASSWORD` 只在首次初始化时写入数据库，改 `.env` 后必须 `docker compose down -v` 再起，否则密码不匹配。

**端口冲突**
改 `.env` 里的 `WEB_PORT`，并在安全组放行同一端口。

## 七、文件说明

| 文件 | 作用 |
|---|---|
| `docker-compose.yml` | 三个服务的编排，只暴露 WEB_PORT |
| `nginx/nginx.conf` | 静态托管 + `/api` 反代 + gzip + 静态缓存 |
| `mysql/my.cnf` | 2G 内存专用调参（buffer pool 256M、关 performance_schema） |
| `backend/Dockerfile` | JRE 21 运行 jar，堆 512M |
| `init/10-data.sql` | 完整库结构与演示数据（本地库导出） |
| `build.ps1` | 本地构建 + 打包 |
| `server-init.sh` | 服务器装 Docker、加 swap、配镜像加速 |
| `.env.example` | 环境变量模板 |
