# 飞书通知与 ChatGPT 登录

管理页面：`http://127.0.0.1:6866/env-config`。这两个功能均在“环境配置”中设置，保存后立即生效。

## 飞书 Webhook

1. 在飞书群添加“自定义机器人”，复制 Webhook 地址。
2. 在“飞书 Webhook”卡片粘贴地址。
3. 若机器人开启了“签名校验”，填写对应签名密钥；未开启时留空。
4. 勾选“启用飞书通知”，点击“保存配置”。

飞书与企业微信各有独立开关，可以同时启用；其中一个渠道失败不会阻止另一个发送。机器人返回业务错误也视为失败。文本使用 JSON 编码，支持换行、引号和 emoji，长消息按 UTF-8 字节安全分段。飞书签名使用当前秒级时间戳与密钥构造 HMAC-SHA256。

配置保存在 SQLite `config` 表，无须替换或重建数据库：

| 配置键 | 用途 |
| --- | --- |
| `FEISHU_HOOK_URL` | 飞书机器人 Webhook |
| `FEISHU_SECRET` | 可选签名密钥 |
| `FEISHU_BOT_IS_SEND` | `true` / `1` 开启，默认关闭 |
| `HOOK_URL` / `BOT_IS_SEND` | 原企业微信配置，继续兼容 |

通知日志不输出 Webhook、签名密钥或机器人原始响应。此项目只提供本机管理页面，配置库仍包含敏感信息，请勿分享数据库。

## 使用 ChatGPT 账号

通过官方 **Codex App Server** 的 ChatGPT OAuth 流程授权，无需填写 API Key 或 API Base URL；使用账号可用的 Codex 模型和额度。

前提：安装官方 Codex CLI，确保启动 Java 服务时 `codex` 在 PATH 中。当前集成已与本机 `codex-cli 0.144.6` 验证握手、登录链接和取消登录。可用官方安装命令 `npm install -g @openai/codex` 安装。

1. 登录方式选择“ChatGPT 登录”。
2. 点击“登录 ChatGPT”，再点击“打开 OpenAI 登录页面”。
3. 在浏览器中完成本人账号授权。网页会自动更新登录状态。
4. 在模型列表选择账号可用模型，或保留“使用账号默认模型”。
5. 点击“保存配置”。原有岗位匹配、打招呼文本生成会通过此通道调用。

`AI_AUTH_MODE=chatgpt` 启用该通道；`CHATGPT_MODEL` 为空时由 Codex 选择账号默认模型。旧数据库未配置 `AI_AUTH_MODE` 时继续使用原 API Key 模式，切换不会删除原 API Key 设置。

岗位描述、你配置的个人介绍及提示词会传给 OpenAI。认证与刷新交由官方 Codex 处理，项目不会抓取 ChatGPT 网页 Cookie，也不会将 OAuth token 当作 OpenAI API Key 使用。

### 登录态与运行方式

- 独立凭证目录：`.chatgpt/auth/`，不会读取或退出桌面 Codex 的账号。
- 工作目录：`.chatgpt/work/`；每次生成使用临时会话，关闭命令执行、浏览器、应用与其他工具能力，仅请求文本回复。
- `.chatgpt/` 已加入 `.gitignore`；不要分享该目录。
- 支持取消登录、退出登录和刷新状态。未登录、断线、上游失败或空回复会明确报错。
- 单次生成最多等待 180 秒（协议请求另有 30 秒超时）；超时会销毁 Codex 子进程。并发生成会明确提示繁忙。
- 应用退出时会一并关闭它创建的 Codex 子进程。
- 本机代理如有需要，可在启动应用前设置 `HTTPS_PROXY`；招聘浏览器仍使用原项目的直连配置。

高级启动参数：

```text
--getjobs.chatgpt.executable=/绝对路径/codex
--getjobs.chatgpt.directory=/独立的本机目录
--getjobs.browser.auto-start=false
```

最后一个参数用于仅启动管理服务进行测试，避免自动打开招聘平台。未配置时维持原有行为。

## 验证

```bash
./gradlew test bootJar
cd front
pnpm install --frozen-lockfile
pnpm exec next build
node scripts/copy-dist.mjs
```

前端更新后，需在项目根目录重新运行 `./gradlew bootJar`，让 jar 包包含新页面。

macOS 的中文项目目录若遇到测试类无法加载，可使用 UTF-8 locale 启动新的 Gradle 进程：

```bash
LC_ALL=en_US.UTF-8 LANG=en_US.UTF-8 ./gradlew --no-daemon test
```

自动测试覆盖飞书签名、JSON 转义、Unicode 分段、渠道隔离和热更新，以及 ChatGPT 模式分流、未登录、授权状态、消息结果提取、RPC 超时和断线。测试不发送真实飞书通知，也不消耗模型额度。真实授权后的模型调用需要用户登录，真实群投递需要用户填写 Webhook 后验证。

参考：[飞书自定义机器人](https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot)、[官方 Codex App Server](https://learn.chatgpt.com/docs/app-server)、[Codex 认证](https://learn.chatgpt.com/docs/auth)。
