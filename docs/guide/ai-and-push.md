# 🤖 AI 大模型与企业微信推送

结合大语言模型与自动化办公机器人，实现高转化率沟通与投递进展实时感知。

---

## 1. AI 智能匹配与打招呼语生成

### 原理简析
传统的死板模板打招呼语回复率往往极低。Getjobs 会在抓取到岗位 JD（职位描述）后，将你的**个人履历亮点**与**目标岗位要求**一起输入给 AI，由 AI 扮演高情商求职者，生成 50~100 字极其精炼、高度契合该岗位的开场白：

<p align="center">
  <img src="/images/AiSayHi.png" alt="AI打招呼效果示例" style="max-width: 80%; border-radius: 8px; border: 1px solid var(--vp-c-divider);" />
</p>

### `.env` 环境变量配置

在项目根目录下创建或编辑 `.env` 文件（或直接在控制面板的“AI 配置”中填写）：

```env
# 企业微信机器人 Webhook 地址（可选）
HOOK_URL=https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=your_wechat_robot_key

# OpenAI 格式兼容的 API 接口基地址
BASE_URL=https://api.openai.com

# 你的 API Key
API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# 调用的模型名称
MODEL=gpt-4o-mini
```

::: tip 💰 费用测算：极低成本日常使用
- `BASE_URL` 支持填入任何兼容 OpenAI 接口标准的地址（如官方直连、OneAPI、NewAPI、DeepSeek 等兼容端点）。
- 推荐使用轻量级快模型（如 `gpt-4o-mini`、`deepseek-chat` 或类似 Nano 系列）。
- 实测在 Boss直聘全天投递满额（150次）的情况下，单日模型 Token 消耗通常**低于 0.05 美元（人民币几分钱）**，成本几乎可以忽略不计。
:::

---

## 2. 企业微信消息通知配置

配置企业微信群机器人后，系统投递全过程将实时推送至你的手机微信，无需时刻守在电脑旁。

### 配置步骤

1. 在企业微信电脑端，任意创建一个内部群聊（两人或以上即可成群）。
2. 点击群设置 -> **“添加群机器人”** -> 新建一个机器人（例如命名为“找工作小助手”）。
3. 复制生成的 **Webhook 地址**。
4. 将该地址粘贴至配置文件中的 `HOOK_URL` 字段即可。

<p align="center">
  <img src="/images/AI配置.png" alt="AI与推送配置" style="max-width: 90%; border-radius: 8px; border: 1px solid var(--vp-c-divider);" />
</p>

### 接收推送效果
- **投递开始与结束提醒**：汇报当日总耗时与各平台进度。
- **匹配成功通知**：推送投递成功的公司名称、岗位名称与打招呼摘要。
- **异常预警**：当 Cookie 失效或出现安全验证码时及时提醒人工接入。
