# run.com.getjobs.api.GetJobsApplication 改造任务清单（Task）

说明：依据当前代码仓库与《Dsign.md》设计，列出分阶段改造任务，并标记实现状态。

状态标识：
- 已实现：当前仓库中已有且可用
- 进行中：已有部分基础，需继续改造
- 未实现：需要新增/替换
- 待核验：存在实现，但需确认是否满足新设计（例如是否已用 Playwright）

---

## 阶段0：基线梳理与一致性修复
- 已实现｜日志落地与滚动策略：logback.xml 输出到 `./target/logs`。
- 已实现｜多平台 Worker 存量代码：`com.getjobs.run.worker/boss|job51|lagou|liepin|zhilian` 模块存在。
- 已实现｜自动化工具封装：`com.getjobs.run.worker/utils/PlaywrightUtil.java`、`com.getjobs.run.worker/utils/SeleniumUtil.java` 存在。
- 已实现｜批量执行脚本：`com.getjobs.run.worker/StartAll.java` 存在（多进程/线程启动）。
- 未实现｜版本一致：Spring Boot 固定 3.5.7、Java 21（pom 仍为 2.5.0）。
- 未实现｜.gitignore：忽略前端产物（.next/out/dist）、node_modules、运行期数据目录。
- 进行中｜设计文档 Dsign.md：已更新为全量 Playwright、配置入库、MVP、双形态 UI。

---

## 阶段1：后端骨架与最小 API
- 未实现｜升级 Spring Boot 至 3.5.7，并新增启动类 `@SpringBootApplication`。
- 未实现｜最小接口：
  - POST `/com.getjobs.run.api/runs` 创建运行
  - GET `/com.getjobs.run.api/runs/{id}/events` SSE 事件流
  - GET `/com.getjobs.run.api/runs/{id}/results` 查询结果
  - GET/PUT `/com.getjobs.run.api/config` 读取/更新配置（DB）
- 未实现｜事件总线与 SSE：定义 `RunEvent` 模型，服务端使用 `SseEmitter` 推送。
- 未实现｜Runner SPI：`WorkerRunner`、`TaskRequest`、`RunEvent` 定义与注册表。
- 未实现｜Boss Runner 接入 SPI，并将关键日志点改为事件 emit。
- 待核验｜Liepin Runner 接入 SPI（需确认是否已为 Playwright 实现）。

---

## 阶段2：持久层（MyBatis-Plus + SQLite）
- 未实现｜引入依赖：`mybatis-plus-boot-starter`、`sqlite-jdbc`；配置数据源与 MP 插件（分页、ID 策略）。
- 未实现｜建表（与 Dsign.md 4.2 对齐）：
  - `task(id, platform, payload_json, status, created_at)`
  - `run(id, task_id, status, started_at, finished_at)`
  - `run_event(id, run_id, level, code, message, data_json, ts)`
  - `job_result(id, run_id, platform, jobTitle, company, success, salary, city, recruiter, msg, href, ts)`
  - `config_global/config_platform/ai_secret/session_store`
- 未实现｜实体/Mapper/Service：为上述表建立实体与 Mapper，统一使用 `IdType.AUTO`。
- 未实现｜ConfigService：配置改为数据库唯一来源，提供读写接口。
- 未实现｜替换存量配置读取：移除 `config.yaml` 与 `.env` 使用，改为调用 ConfigService（涉及 `AiService`、各平台 `*Config`）。
- 进行中｜运行期目录规范：将 cookies、data、snapshots 统一至 `./target/data/...`，替换源码中硬编码路径（如 Boss 中的 cookie/data 路径）。

---

## 阶段3：平台迁移与统一 Playwright
- 已实现｜Boss：基于 Playwright 的核心流程（登录、搜索、过滤、发起聊天）。
- 待核验｜Liepin：存在模块（`com.getjobs.run.worker/liepin/*`），需确认是否已使用 Playwright 并接入 SPI。
- 未实现｜智联（`com.getjobs.run.worker/zhilian/*`）：迁移至 Playwright，替换 Selenium 依赖与选择器策略。
- 未实现｜51job（`com.getjobs.run.worker/job51/*`）：迁移至 Playwright，优化滚动与风控处理。
- 未实现｜Lagou（`com.getjobs.run.worker/lagou/*`）：暂停或迁移（按设计文档为“暂停维护”，需在代码与文档中标注）。
- 未实现｜移除 Selenium 依赖链与 `SeleniumUtil`（待上述迁移完成后删除）。
- 未实现｜统一选择器与重试策略：多选择器冗余、超时重试、指数退避、关键步骤截图与事件化。

---

## 阶段4：UI 集成（不依赖 Nginx）
- 未实现｜Next.js 静态导出配置：`basePath='/app'`、`trailingSlash=true`、`output='export'`，客户端通过 `/com.getjobs.run.api/...` 获取数据。
- 未实现｜UI 分发与安装：通过 GitHub Release 提供 `out/app` 压缩包，用户下载并解压。
- 未实现｜Spring Boot 提供静态 UI（可选）：
  - 属性：`ui.mode=embedded|external`（默认 embedded）
  - 属性：`ui.staticPath=<绝对路径>` 指向外部 dist 目录
  - ResourceHandler 将 `/app/**` 映射到 `ui.staticPath` 或 `classpath:/static/app/`
  - SPA 回退：`/app/**` 非文件请求转发到 `/app/index.html`
- 未实现｜CORS 策略：仅开发模式放开本地源；生产同源关闭。
- 未实现｜SSE 透传验证：前端 `EventSource('/com.getjobs.run.api/runs/{id}/events')` 正常接收。

---

## 阶段5：稳定性与体验
- 未实现｜事件码规范与分级：`LOGIN_OK/LIMIT_HIT/CHAT_SENT/FETCHED/...`，在 Runner 中统一上报。
- 未实现｜黑名单、薪资、学历等过滤条件数据化：由 DB 配置驱动，前端可视化配置。
- 未实现｜错误快照：关键失败处保存截图/HTML 到 `./target/data/snapshots/<runId>/...`，并在事件中附链接。
- 未实现｜任务与运行统计：简单计数与最近 N 次结果展示（UI/接口）。
- 未实现｜文档对齐：更新 README、doc/Detail.md 的运行/安装/打包说明（不提交 dist 入库）。

---

## 代码级改造指引（按现有仓库情况）
- pom.xml：
  - 未实现｜升级 Spring Boot 2.5.0 → 3.5.7，并新增 MyBatis-Plus、sqlite-jdbc 依赖。
  - 未实现｜待迁移完成后移除 Selenium 相关依赖。
- 入口与配置：
  - 未实现｜新增 `Application` 启动类，组织 Controller/Service 配置。
  - 未实现｜新增 `application.properties` 属性：`ui.mode`、`ui.staticPath`、SQLite 数据源。
- 资源与路径：
  - 未实现｜城市/行业编码等改为 classpath 读取，移除硬编码源码路径。
  - 进行中｜日志与运行期目录规范化，避免写入 `src/main/java/...`。

---

## 快速里程碑
- 里程碑1（后端骨架 + Boss 接入）：阶段1 完成 + Boss Runner 事件化，UI 可通过 SSE 看到运行进度。
- 里程碑2（持久化 + Liepin 对齐）：阶段2 完成 + Liepin 接入 SPI，结果与事件全部入库。
- 里程碑3（平台迁移）：智联、51 完成 Playwright 迁移，移除 Selenium 依赖。
- 里程碑4（UI 装配）：完成 UI dist 分发、`ui.staticPath` 映射与安装指南。
- 里程碑5（稳定性）：统一事件码、快照与过滤配置数据化，完善文档。

---

## 附：当前代码快照（用于定位）
- `com.getjobs.run.worker/StartAll.java`（存在）
- `com.getjobs.run.worker/boss/*`（存在，Boss 主流程）
- `com.getjobs.run.worker/job51/*`、`com.getjobs.run.worker/lagou/*`、`com.getjobs.run.worker/liepin/*`、`com.getjobs.run.worker/zhilian/*`（存在）
- `com.getjobs.run.worker/utils/PlaywrightUtil.java`、`com.getjobs.run.worker/utils/SeleniumUtil.java`（存在）
- `src/main/resources/logback.xml`（存在）
- Spring Boot 启动类与 API 层（未发现）
