# 并发压测说明（JMeter）

对应开题报告质量要求：**「常规数据量下主要接口响应不超过 2 秒」**。
单线程均值回答不了这句话——必须给出并发下的响应时间与 TPS。

## 三个场景与口径

| 场景 | 接口 | 并发 | 循环 | 说明 |
|---|---|---|---|---|
| 01 登录 | `POST /api/auth/login` | 50 | 10 | 涉及 BCrypt 校验，是 CPU 敏感型接口 |
| 02 计划列表分页 | `GET /api/train/plan/page` | 50 | 10 | 典型业务查询，代表「主要接口」 |
| 03 AI 方案生成 | `POST /api/ai/plan/generate` | 50 | 2 | **含大模型推理耗时，必须与上两项分表统计** |

> 第三项不要和前两项混在一张表里。规则模式约 30–40 ms，混合模式约 2.9 s，
> 两者混算会得出「平均 1.5 s、达标」这种自欺欺人的结论。
> 论文里正确写法是：业务接口（不含 AI）P95 ≤ 2 s 达标；AI 生成接口因含模型推理，
> 单列一张表并注明其耗时构成。

## 运行

```bash
# 1. 启动后端（8000）与数据库
cd work/app && start-all.bat

# 2. 命令行压测，生成 HTML 报告
jmeter -n -t work/app/jmeter/ai-training-test-plan.jmx -l result.jtl -e -o report

# 3. 打开 report/index.html 看聚合报告
```

可调参数（`-J` 覆盖全局变量）：

```bash
jmeter -n -t ai-training-test-plan.jmx \
  -Jhost=127.0.0.1 -Jport=8000 \
  -Jconcurrency=50 -Jloops=10 \
  -JuserId=2 -JplanMode=HYBRID -Jrefresh=true \
  -l result.jtl -e -o report
```

- `-Jrefresh=true` 会绕过结果缓存，测的是每次都真实调用模型的最坏情况；
  设为 `false` 可观察缓存命中后的接口耗时（应回落到几十毫秒）。
- `-JplanMode` 可切 `RULE` / `HYBRID` / `LLM_ONLY`，用于对比三种模式的耗时。

## 记录口径

从 JMeter 的 HTML 报告里抄三列进论文：**Average、95th pct、Throughput(TPS)**，
外加 Error %。断言已内置「返回体 `"code":0`」（本项目统一返回结构里 0 表示成功，
不是 HTTP 200），出现业务错误码会记为失败样本。
