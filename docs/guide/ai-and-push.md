# 🤖 AI 大模型与企业微信推送

结合大语言模型与自动化办公机器人，辅助生成沟通文本并接收投递通知。

---

## 1. AI 智能匹配与打招呼语生成

### 原理简析
传统的死板模板打招呼语回复率往往极低。Get Jobs 会在抓取到岗位 JD（职位描述）后，将你的**个人履历亮点**与**目标岗位要求**一起输入给 AI，由 AI 扮演高情商求职者，辅助撰写个性化的开场白。README 说明该能力目前仅限 Boss直聘：

<p align="center">
  <img src="/images/AiSayHi.png" alt="AI打招呼效果示例" style="max-width: 80%; border-radius: 8px; border: 1px solid var(--vp-c-divider);" />
</p>

### `.env` 环境变量配置

在项目根目录下创建或编辑 `.env` 文件（或直接在控制面板的“AI 配置”中填写）：

```dotenv
# 企业微信机器人 Webhook 地址（可选）
HOOK_URL=https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=your_wechat_robot_key

# OpenAI 格式兼容的 API 接口基地址
BASE_URL=https://api.openai.com

# 你的 API Key
API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# 调用的模型名称
MODEL=gpt-5-nano
```

::: tip AI 服务与费用
README 的示例模型为 `gpt-5-nano`，其中记录的一次测试约为每天 0.06 美元（6 美分）。这只是作者当时的测试参考，不代表当前价格或你的实际消耗；用量、模型及服务商都会影响账单。

`BASE_URL` 可使用兼容 OpenAI 接口格式的官方地址或自行选择的服务地址。项目不绑定、不推荐第三方代理商。AI 服务费用与项目免费非商业使用是两回事。
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

### 使用与排查

填写并保存 `HOOK_URL` 后，运行一次任务检查通知是否送达。具体通知内容以当前版本实际输出为准。没有收到通知时，检查机器人地址和网络，并查看运行日志。

::: warning 保护密钥
`API_KEY` 与带 key 的 Webhook 地址均属私密配置，不要提交到 Git、截图公开或发送到交流群。
:::
