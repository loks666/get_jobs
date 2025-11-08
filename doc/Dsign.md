# 🏗️ 《run.com.getjobs.api.GetJobsApplication 系统概要设计文档（修订版）》

---

## 1. 系统概述

### 1.1 背景与目标

**run.com.getjobs.api.GetJobsApplication** 是一个基于 **Spring Boot + Next.js + Playwright** 的自动化智能求职系统，
用于在多个招聘网站上自动执行岗位搜索、筛选、投递和状态跟踪。

系统提供从「任务创建 → 平台投递 → 结果落库 → 状态回显」的完整闭环。

### 1.2 系统总体目标

* 提供跨平台自动化投递功能（Boss、51Job、猎聘、智联、拉勾）
* 支持多用户任务配置、Cookie 管理、AI 打招呼语生成
* 实现任务状态实时回传（SSE）
* 使用 SQLite 管理任务与配置，保证部署简洁、安全可控

---

## 2. 系统总体架构

系统采用 **三层分离架构**：

```
┌──────────────────────────────────────────┐
│ 前端层（Front） - Next.js, port=6680     │
│  - 任务创建、投递监控、配置管理界面      │
└──────────────────────────────────────────┘
                 │ HTTP / SSE
                 ▼
┌──────────────────────────────────────────┐
│ 后端层（API） - Spring Boot 3.5.7, port=8888 │
│  - 任务调度、数据库访问、状态推送        │
│  - 整合 Worker 模块执行自动化逻辑        │
│  - 提供 REST / SSE 接口供前端使用        │
└──────────────────────────────────────────┘
                 │ 内部模块调用
                 ▼
┌──────────────────────────────────────────┐
│ 执行层（Worker） - Java + Playwright     │
│  - 负责自动登录、岗位抓取、智能投递      │
│  - 每个平台封装为独立模块（boss、51等） │
│  - 提供 Runner 接口供 Spring 调度        │
└──────────────────────────────────────────┘
```

---

## 3. 模块划分

### 3.1 前端（Front）

| 模块       | 技术                | 功能                     |
| -------- | ----------------- | ---------------------- |
| **任务中心** | Next.js + React   | 用户选择平台、关键词、城市并创建任务     |
| **执行监控** | SSE + React 状态流   | 实时显示投递进度与结果            |
| **配置管理** | 前端调用后端 Config API | 管理 API Key、Cookie、代理配置 |
| **任务日志** | Tail 模式           | 展示执行结果、错误、时间统计         |

**端口：** `6680`
**交互方式：**

* `POST /com.getjobs.run.api/runs` 创建任务
* `GET /com.getjobs.run.api/runs/{id}/events` 订阅指定运行的 SSE 实时推送

---

### 3.2 后端（API）

| 模块                   | 主要类                                              | 职责                  |
| -------------------- | ------------------------------------------------ | ------------------- |
| **JobController**    | `com.getjobs.com.getjobs.run.api.controller.JobController`       | 接收前端任务请求、分配任务 ID、落库 |
| **WorkerController** | `com.getjobs.com.getjobs.run.worker.controller.WorkerController` | 提供执行触发入口            |
| **WorkerService**    | `com.getjobs.com.getjobs.run.worker.service.WorkerService`       | 调度各平台执行器（Runner）    |
| **Database 模块**      | SQLite（持久层方案待定：JDBC/JOOQ/MyBatis-Plus） | 存储任务、配置、日志、黑名单      |
| **SSE 模块**           | Spring MVC SSEEmitter                            | 向前端推送任务状态           |
| **ConfigService**    | 统一管理 API Key / Cookie / Token 等敏感配置              |                     |

**端口：** `8888`
**职责：**

* 提供统一任务入口；
* 管理 SQLite 文件数据库；
* 控制 Worker 执行；
* 推送实时状态流至前端。

---

### 3.3 执行层（Worker）

| 子模块          | 功能          | 主要类                                 |
| ------------ | ----------- | ----------------------------------- |
| **boss/**    | Boss直聘自动化逻辑 | `Boss.java`, `BossConfig`           |
| **job51/**   | 前程无忧        | `Job51.java`, `Job51Config`         |
| **liepin/**  | 猎聘          | `Liepin.java`                       |
| **lagou/**   | 拉勾          | `Lagou.java`                        |
| **zhilian/** | 智联招聘        | `Zhilian.java`                      |
| **ai/**      | AI 打招呼语     | `AiService`                         |
| **utils/**   | 通用工具类       | `PlaywrightUtil`, `JobUtils`, `Bot` |
| **core/**    | 调度接口层       | `BossRunner`, `Job51Runner` 等       |

**技术栈：** 全量使用 Playwright；不再依赖 Selenium/chromedriver。

**执行逻辑：**

1. 初始化浏览器上下文；
2. 登录目标平台；
3. 抓取岗位列表；
4. 过滤黑名单、判断薪资；
5. 自动发送智能打招呼语；
6. 投递结果写入数据库；
7. 调用回调接口上报状态。

---

## 4. 数据库设计（SQLite）

### 4.1 文件结构

```
/data/getjobs.db          # 任务与日志数据
/config/config.db         # 敏感配置数据（API Key、Cookie）
```

### 4.2 主要表结构（概要）

| 表名            | 用途      | 主要字段 |
| ------------- | --------- | ------ |
| `task`        | 投递任务表   | `id`, `platform`, `payload_json`, `status`, `created_at` |
| `run`         | 运行实例表   | `id`, `task_id`, `status`, `started_at`, `finished_at` |
| `run_event`   | 运行事件表   | `id`, `run_id`, `level`, `code`, `message`, `data_json`, `ts` |
| `job_result`  | 投递结果表   | `id`, `run_id`, `platform`, `jobTitle`, `company`, `success`, `salary`, `city`, `recruiter`, `msg`, `href`, `ts` |
| `config_global`  | 全局配置   | `id`, `key`, `value_json`, `updated_at` |
| `config_platform`| 平台配置   | `id`, `platform`, `key`, `value_json`, `updated_at` |
| `ai_secret`      | AI 密钥   | `id`, `name`, `enc_value`, `updated_at` |
| `session_store`  | 会话存储   | `id`, `platform`, `user_data_dir`, `cookies_json`, `updated_at` |

### 4.3 安全策略

* **敏感字段（API Key、Cookie）** 存储在独立数据库文件 `config.db`；
* **Git 忽略规则：**

  ```
  data/
  config/config.db
  ```
* **初始化文件**：

    * `config/schema.sql`（仅含表结构）
    * `config/init_data.sql`（占位符配置）

### 4.4 运行模式

* 单文件 SQLite（默认）
* WAL 模式开启，提高并发性能
* 定期执行 `VACUUM` 优化文件体积

---

## 5. 任务执行流程

```text
用户点击投递 ▶ 前端调用 /com.getjobs.run.api/runs ▶
Spring Boot 创建 runId ▶
保存任务与运行记录到数据库 ▶
WorkerService 调用对应 Runner ▶
Runner 执行 Playwright 自动化投递 ▶
Runner 通过事件流 emit 上报进度与结果 ▶
数据库更新状态与结果 ▶
SSE 推送前端 ▶ 前端刷新任务状态
```

**RunID 格式：**

```
{PLATFORM}-{yyyyMMdd}-{HHmmss}-{SEQ}
例如：BOSS-20251028-153010-001
```

---

## 6. 配置与安全设计

### 6.1 配置管理策略（数据库为唯一来源）

- 不再使用 `config.yaml` 或 `.env` 文件。
- 所有配置（全局、平台、AI、会话）存储在 SQLite 数据库中，通过后端 API 读写。
- 前端仅作为 UI，调用后端 Config API 完成配置管理。

### 6.2 配置相关表（概要）

- `config_global(id, key, value_json, updated_at)`
- `config_platform(id, platform, key, value_json, updated_at)`
- `ai_secret(id, name, enc_value, updated_at)`
- `session_store(id, platform, user_data_dir, cookies_json, updated_at)`

### 6.3 敏感信息与加密

- AI Key、Cookie 等敏感字段使用对称加密存储（进程启动时解密）。
- 数据库文件与快照目录加入 Git 忽略；生产环境可选 SQLCipher。

### 6.4 资源与路径规范

- 城市/行业编码等静态资源通过 classpath 读取，避免硬编码源码路径。
- 运行期目录统一：`./target/data/{cookies,results,snapshots}`。
- 日志目录：`./target/logs`，文件名通过系统属性 `log.name` 区分模块。

### 6.5 不再使用项

- 不再加载 `config.yaml`/`.env`；相关依赖与文档将移除或标注过渡。

---

## 7. 系统运行模式

| 模式               | 描述                    | 使用场景    |
| ---------------- | --------------------- | ------- |
| **开发模式（单进程）**    | Worker 嵌入 Spring Boot | 本地调试    |
| **生产模式（双进程）**    | Worker 独立运行（端口 5668）  | 部署分布式执行 |
| **独立 Worker 模式** | 直接运行 Runner 类         | 单机批量任务  |

---

## 8. 日志与监控

| 模块         | 日志位置                | 说明          |
| ---------- | ------------------- | ----------- |
| Playwright | `./target/logs`     | 按 `log.name` 生成平台独立日志 |
| API        | `./target/logs`     | 统一由 logback 滚动输出 |
| SSE 通信     | 内存流 + 数据库事件        | 实时任务状态与关键节点追踪 |
| 报错记录       | 数据库 `run_event/job_result` | 用于前端展示与审计 |

---

## 9. 可扩展性设计

| 扩展方向  | 内容                     | 说明               |
| ----- | ---------------------- | ---------------- |
| 新增平台  | 新建模块 `com.getjobs.run.worker/newsite`  | 实现 Runner 接口即可接入 |
| 数据分析  | 对接 DuckDB / PostgreSQL | 支持批量投递统计         |
| 安全加密  | 集成 SQLCipher           | 防止配置泄露           |
| 调度系统  | 支持 Quartz / Redis 队列   | 异步并行执行任务         |
| 容器化部署 | Dockerfile + Compose   | 一键启动全栈环境         |

---

## 10. 总结

| 模块  | 技术栈                        | 特点            |
| --- | -------------------------- | ------------- |
| 前端  | Next.js + React            | 动态交互、SSE 实时推送 |
| 后端  | Spring Boot 3.5.7 +（持久层方案待定） | 调度、数据库、状态服务   |
| 执行层 | Java + Playwright          | 多平台自动化脚本      |
| 数据层 | SQLite (可加密)               | 单文件存储、轻量安全    |

---

## 11. MVP 范围与里程碑

### 11.1 MVP 范围（本次改造目标）

- 平台覆盖：Boss、猎聘、智联、51job 全部基于 Playwright 的 Runner。
- 配置：数据库作为唯一来源（全局/平台/AI/会话），提供 Config API 读写。
- API：`POST /runs`、`GET /runs/{id}/events`(SSE)、`GET /runs/{id}/results`、`GET/PUT /config`。
- 事件：统一 `RunEvent` 模型，Runner 通过 emit 上报，SSE 推送前端并入库。
- 数据：SQLite 初版表（task/run/run_event/job_result/config_*）。
- UI：任务创建页、运行详情（SSE）、结果列表。

### 11.2 非目标（MVP 暂不覆盖）

- 分布式/消息队列、容器化编排、复杂统计报表、全自动过人机验证。

### 11.3 里程碑

- 里程碑1：Runner SPI 与最小 API 打通（Boss/猎聘接入事件流，SQLite 建表与入库）。
- 里程碑2：Next.js UI 三页上线；智联/51 完成 Playwright 迁移；移除 Selenium 依赖与文档。
- 里程碑3：稳定性与风控兜底、日志与快照规范完善、文档与目录最终对齐。

---

## 12. 前端运行与打包模式（双形态）

### 12.1 目标

- 开发阶段：前端与后端可独立启动，提升开发效率。
- 部署阶段：可将前端产物嵌入 Spring Boot 可执行包中，做到一体化分发；或选择独立部署。

### 12.2 模式说明

- Embedded 模式（嵌入式）：
  - Next.js 采用 `next export` 生成纯静态产物。
  - 产物拷贝至 Spring Boot `classpath:/static/app/`（或 `public/app/`）。
  - 访问 `http://<host>:8888/app/` 即可打开 UI，前端通过相对路径访问同源 API/SSE。

- External 模式（外置式）：
  - 开发时运行 `next dev`（如 `http://localhost:6680`）。
  - 前端通过代理将 `/com.getjobs.run.api/*` 转发到 `http://localhost:8888`，避免跨域与 CORS 配置复杂度。
  - 生产也可选择将静态站点部署在任意静态服务器上，配置环境变量指向 API。

### 12.3 路由与资源策略

- 不使用 SSR，统一使用静态导出（`next export`）。
- SPA 路由回退：Spring Boot 配置将 `/app/**` 非文件路径转发到 `/app/index.html`。
- 静态资源缓存：`index.html` 禁缓存，`/app/_next/*` 与图片等开启长缓存。

### 12.4 API Base 选择策略

- 默认同源：UI 通过相对路径 `/com.getjobs.run.api/...` 调用后端。
- 开发或外置部署：读取 `NEXT_PUBLIC_API_BASE` 指定 API 根地址（例如 `http://localhost:8888`）。
- 代码中封装 `getApiBase()`：优先环境变量，否则回退 `window.location.origin`。

### 12.5 开发模式建议

- 启动 Spring Boot（:8888）。
- 启动 Next.js dev（:6680 或 :3000），在 `next.config.js` 中配置代理：
  - 将 `/com.getjobs.run.api/:path*` 重写到 `http://localhost:8888/com.getjobs.run.api/:path*`；SSE 路径同理。
- Spring Boot 开启仅开发可用的 CORS（允许本地前端源）。

### 12.6 部署模式建议（嵌入式）

- 前端执行 `next build && next export` 生成 `out/`。
- 构建流程在 Maven `package` 阶段调用前端构建，并将 `out/` 拷贝到 `src/main/resources/static/app/`（或直接拷贝到 `target/classes/static/app/`）。
- 运行 Jar 后，访问 `/app/` 加载 UI，使用同源 API 与 SSE。

### 12.7 SSE 与长连接

- 统一通过 `EventSource('/com.getjobs.run.api/runs/{id}/events')` 订阅事件。
- 若为外置部署，确保代理层对 `text/event-stream` 头的透传与连接保持。
- 服务端使用 Spring SseEmitter，心跳与超时合理设置，避免连接早断。

### 12.8 安全与 CORS

- 生产环境使用同源部署（嵌入式）时，可关闭 CORS 以降低攻击面。
- 仅在开发环境开放 CORS；或始终通过前端代理转发来避免跨域。
