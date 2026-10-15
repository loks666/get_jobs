# 🚀 快速开始

只需 5 分钟，带你完成从环境搭建、数据库配置到首次自动投递的全流程。

---

## 1. 环境准备

在开始之前，请确保你的本地计算机满足以下环境要求：

| 组件 | 版本要求 | 下载与配置说明 |
| :--- | :--- | :--- |
| **操作系统** | Windows 10/11 / macOS / Linux | 需具备桌面浏览器图形环境（用于扫码与自动化交互） |
| **JDK** | **JDK 21** 及以上 | 推荐 [Eclipse Temurin JDK 21](https://adoptium.net/temurin/releases/?version=21) |
| **构建工具** | Gradle 9.x（或使用自带 `gradlew`） | 项目根目录已集成 Gradle Wrapper，无需全局安装 |
| **浏览器环境**| Chrome / Chromium | 程序会自动判断操作系统并自动匹配下载驱动 |

::: danger ❌ 核心警告：禁止服务器部署 & 关闭代理
1. **不要在云服务器（如阿里云、腾讯云等机房 IP）运行**：各大招聘网站对机房 IP 设有极其严格的风控盾，服务器 IP 无法获取正常岗位数据。
2. **务必关闭国外代理/科学上网**：招聘平台多部署在国内，开启系统代理会导致页面加载超时或验证码频繁触发。
:::

---

## 2. 拉取项目源码

使用 Git 克隆主仓库代码到本地：

```bash
git clone https://github.com/loks666/get_jobs.git
cd get_jobs
```

---

## 3. 初始化数据库（新用户必做 ⭐）

为了避免开发者将个人配置与私密登录态误上传至仓库，Git 仓库中**不包含预置的数据库实体文件**。

首次使用必须按以下步骤初始化 SQLite 数据库：

1. 打开项目的 [GitHub Releases 最新发布页](https://github.com/loks666/get_jobs/releases/latest)。
2. 下载资源文件中的 `getjobs.db.template`。
3. 将下载的文件**重命名为 `getjobs.db`**。
4. 在项目根目录下创建 `db` 文件夹，并将重命名后的 `getjobs.db` 放入其中：
   ```bash
   mkdir -p db
   # 最终确认文件路径为: get_jobs/db/getjobs.db
   ```

::: tip 💡 数据库结构说明
该空库已预先建好 16 张核心业务数据表，并内置了全国主流城市、岗位类别、行业经验、学历等 670+ 条预设数据。你的 Cookie 与投递记录均会安全地保留在本地该文件中。

**⚠️ 老用户更新注意**：升级拉取新代码时，切勿覆盖现有的 `db/getjobs.db`，否则已保存的配置和登录态将被清空。
:::

---

## 4. 准备图片简历（可选）

如果你希望在 Boss直聘打招呼后**自动附带发送个人简历图片**：

1. 将你的 PDF 简历导出为高清单张 JPG 图片。
2. 将该图片重命名为 `resume.jpg`。
3. 放置到项目 `src/main/resources/` 目录下（最终路径为 `src/main/resources/resume.jpg`）。
4. 在系统配置中将 `sendImgResume` 项开启即可。

---

## 5. 启动与使用

### 启动后端服务

在项目根目录下通过 Gradle Wrapper 直接启动：

::: code-group
```bash [Windows PowerShell]
.\gradlew.bat bootRun
```

```bash [Linux / macOS]
./gradlew bootRun
```

```bash [直接运行主类]
# 也可以用 IntelliJ IDEA 打开项目，找到并直接运行：
# src/main/java/com/getjobs/GetJobsApplication.java
```
:::

启动成功后，控制台将输出服务启动完成日志。

### 访问前端控制面板

- 前端已编译好的静态资源默认会直接随 release 分发，你可以直接访问本地端口（如 `http://localhost:8888` 或 `http://localhost:6680`）。
- 在网页控制台中进行关键词配置、岗位薪资限定、扫码登录平台，随后点击**“开始投递”**即可！

投递日志与运行情况可在页面中实时查看：

<p align="center">
  <img src="/images/运行日志.png" alt="运行日志" style="border-radius: 8px; border: 1px solid var(--vp-c-divider);" />
</p>
