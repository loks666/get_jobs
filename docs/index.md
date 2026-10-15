---
layout: home

hero:
  name: "Get Jobs"
  text: "让 AI 替你海投简历"
  tagline: "把时间留给面试准备 · 全平台自动化投递 · AI 智能定制打招呼语 · 精准企业黑名单过滤"
  actions:
    - theme: brand
      text: 🚀 5分钟快速上手
      link: /guide/quick-start
    - theme: alt
      text: ⭐ GitHub 点赞支持
      link: https://github.com/loks666/get_jobs

features:
  - icon: 🌐
    title: 全平台覆盖
    details: 覆盖 Boss直聘、猎聘等招聘平台；当前主要维护 Boss 与猎聘，其他平台已停止维护。
  - icon: 🤖
    title: AI 智能打招呼
    details: 接入兼容 OpenAI 格式的大模型，基于个人求职简历与岗位 JD 动态撰写打招呼语，目前仅限 Boss直聘。
  - icon: 🛡️
    title: 黑名单与精准过滤
    details: 智能跳过不活跃 HR、猎头岗位、外包公司，自动将沟通不顺企业加入黑名单，拒绝无效沟通。
  - icon: 📱
    title: 实时消息推送
    details: 支持企业微信 Webhook 实时通知，投递状态、沟通反馈随时掌握，不在电脑前也能洞察全局。
  - icon: 💾
    title: 本地数据持久化
    details: 基于轻量级 SQLite 存储配置与投递日志，配置与投递记录保留在本机，方便追踪投递进度。
  - icon: ⚡
    title: 优雅开箱即用
    details: 配套直观的前端 Web GUI 管理面板，开箱即用，无需繁琐调试即可轻松开启批量投递。
---

<div style="text-align: center; margin-top: 2rem;">

### 📊 社区实时动态

<div class="readme-badges">

<!--@include: ./snippets/badges.md-->

</div>

</div>

---

## 🖥️ 看得见的求职工作台

从配置到岗位分析，再到投递日志，在网页端完成日常操作。

![Get Jobs 运行日志](/images/运行日志.png)

[浏览全部界面截图 →](/guide/screenshots) · [了解完整功能 →](/guide/introduction)

::: warning 使用前先了解
当前主要维护 **Boss直聘、猎聘**；智联招聘、前程无忧和拉勾已停止维护。README 建议 Boss 仍以手动投递为主，本项目用于 PC 端辅助。非商业使用免费，AI 接口费用由所选服务商收取。
:::

## ⚡ 三步极简启动

仅需 3 步，即可在本地启动 AI 自动求职引擎：

```bash
# 1. 克隆项目仓库
git clone https://github.com/loks666/get_jobs.git
cd get_jobs

# 2. 放置空数据库文件（从 Release 下载 getjobs.db.template 重命名为 getjobs.db）
mkdir -p db
# 将下载的 getjobs.db 放入 db/ 目录

# 3. 启动项目后端
./gradlew bootRun
```

::: tip 💡 提示
启动后访问本地管理面板，配置好个人城市、期望薪资及 AI Key，即可开启全自动投递体验。详细步骤请查看 [5分钟快速上手教程](/guide/quick-start)。
:::

---

## 📈 Star 增长趋势

感谢广大开发者与打工人们的支持与喜爱，黑暗无论多么长，光明迟早总是会来的：

<p align="center" style="margin-top: 1.5rem;">
  <a href="https://star-history.com/#loks666/get_jobs&Date" target="_blank">
    <img src="https://api.star-history.com/svg?repos=loks666/get_jobs&type=Date" alt="Get Jobs Star History" style="max-width: 100%; border-radius: 8px;" />
  </a>
</p>

<div style="text-align: center; margin-top: 2rem; color: var(--vp-c-text-2);">
  <em>"每一个伟大，都有一个平凡的开始。黎明破晓之时，苦难都将化作勋章。"</em>
</div>

## 💬 与社区一起前进

欢迎交流求职经验、技术问题与使用心得。

- [加入 QQ 交流群](https://qm.qq.com/q/qJwmIrqPU)
- [社区、贡献流程与赞赏支持](/guide/community)
- [作者的其他开源项目](/guide/resources)
- [查看更新日志](/guide/changelog)

<a href="https://trendshift.io/repositories/9608"><img src="https://trendshift.io/api/badge/trendshift/repositories/9608/daily?language=Java" alt="#1 Java Repository Of The Day" height="55" /></a>
