# Playwright Manager 使用指南

## 概述

`PlaywrightManager` 是一个Spring管理的单例Bean，在应用启动时自动初始化Playwright实例，并在应用关闭时自动清理资源。

## 主要特性

- ✅ **自动启动**：Spring Boot启动时自动初始化Playwright
- ✅ **自适应屏幕**：自动检测系统屏幕尺寸并适配
- ✅ **资源管理**：应用关闭时自动释放所有资源
- ✅ **依赖注入**：可在任何Spring Bean中注入使用
- ✅ **桌面/移动双模式**：支持桌面和移动设备浏览器模拟

## 架构设计

```
com.getjobs.worker
└── manager/
    └── PlaywrightManager.java    # Playwright管理器（Spring Bean）
```

## 使用方法

### 1. 在Service或Controller中注入

```java
@Service
public class YourService {
    private final PlaywrightManager playwrightManager;

    public YourService(PlaywrightManager playwrightManager) {
        this.playwrightManager = playwrightManager;
    }

    public void doSomething() {
        // 获取桌面页面
        Page page = playwrightManager.getDesktopPage();
        page.navigate("https://www.example.com");

        // 获取页面标题
        String title = page.title();
        System.out.println("页面标题: " + title);
    }
}
```

### 2. 获取不同类型的实例

```java
// 获取桌面页面（默认已创建）
Page desktopPage = playwrightManager.getDesktopPage();

// 获取移动页面（懒加载，首次调用时创建）
Page mobilePage = playwrightManager.getMobilePage();

// 获取浏览器上下文
BrowserContext desktopContext = playwrightManager.getDesktopContext();
BrowserContext mobileContext = playwrightManager.getMobileContext();

// 获取浏览器实例
Browser browser = playwrightManager.getBrowser();

// 获取Playwright实例
Playwright playwright = playwrightManager.getPlaywright();
```

### 3. 获取屏幕信息

```java
// 获取检测到的屏幕宽度
int width = playwrightManager.getScreenWidth();

// 获取检测到的屏幕高度
int height = playwrightManager.getScreenHeight();

// 检查是否已初始化
boolean initialized = playwrightManager.isInitialized();
```

## API接口测试

启动应用后，可以通过以下接口测试Playwright功能：

### 查看状态

```bash
GET http://localhost:8080/api/playwright/status
```

返回示例：
```json
{
  "initialized": true,
  "screenWidth": 1920,
  "screenHeight": 1080,
  "hasDesktopPage": true,
  "hasBrowser": true
}
```

### 测试导航

```bash
GET http://localhost:8080/api/playwright/test-navigate
```

返回示例：
```json
{
  "success": "true",
  "title": "百度一下，你就知道",
  "url": "https://www.baidu.com/"
}
```

## 配置说明

### 默认配置

- **浏览器类型**：Chromium
- **模式**：非无头模式（headless=false），便于调试
- **减速**：50ms（slowMo=50），便于观察操作
- **超时时间**：30秒
- **桌面视口**：自适应屏幕大小
- **移动视口**：375x812（iPhone X尺寸）

### 自定义配置

如需修改配置，可在 `PlaywrightManager.java` 的 `init()` 方法中调整：

```java
// 修改为无头模式
browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
    .setHeadless(true)  // 改为true
    .setSlowMo(0));     // 取消减速

// 修改超时时间
desktopPage.setDefaultTimeout(60000);  // 60秒
```

## 生命周期管理

### 初始化时机

- `@PostConstruct` 标注的 `init()` 方法在Spring容器启动后自动执行
- 初始化顺序：检测屏幕 → 创建Playwright → 创建Browser → 创建Context → 创建Page

### 销毁时机

- `@PreDestroy` 标注的 `destroy()` 方法在应用关闭前自动执行
- 销毁顺序：Page → Context → Browser → Playwright

### 日志输出

初始化成功时会输出：
```
开始初始化Playwright管理器...
检测到屏幕尺寸: 1920x1080
Playwright实例创建成功
浏览器实例创建成功
桌面浏览器上下文创建成功（视口大小: 1920x1080）
移动设备浏览器上下文创建成功
桌面浏览器页面创建成功
Playwright管理器初始化完成！
```

## 与旧版PlaywrightUtil对比

| 特性 | PlaywrightUtil（旧） | PlaywrightManager（新） |
|------|---------------------|------------------------|
| 管理方式 | 静态工具类 | Spring Bean |
| 初始化 | 手动调用init() | 自动初始化 |
| 资源释放 | 手动调用close() | 自动释放 |
| 屏幕适配 | 固定1920x1080 | 自动检测 |
| 依赖注入 | 不支持 | 支持 |
| 生命周期 | 手动管理 | Spring管理 |

## 注意事项

1. **无头环境**：在无头环境（如Docker容器）中，无法检测屏幕尺寸，会使用默认值1920x1080
2. **资源占用**：Playwright实例会占用一定系统资源，建议在生产环境按需配置
3. **线程安全**：PlaywrightManager是单例Bean，多线程访问时需要注意Page对象的并发使用
4. **懒加载**：移动页面采用懒加载策略，首次调用getMobilePage()时才创建

## 迁移指南

如果你之前使用的是 `PlaywrightUtil`，可以按以下方式迁移：

### 旧代码
```java
public class OldClass {
    public void doWork() {
        PlaywrightUtil.init();  // 手动初始化
        PlaywrightUtil.navigate("https://example.com", DeviceType.DESKTOP);
        PlaywrightUtil.close();  // 手动关闭
    }
}
```

### 新代码
```java
@Service
public class NewService {
    private final PlaywrightManager playwrightManager;

    public NewService(PlaywrightManager playwrightManager) {
        this.playwrightManager = playwrightManager;
    }

    public void doWork() {
        // 无需手动初始化和关闭
        Page page = playwrightManager.getDesktopPage();
        page.navigate("https://example.com");
    }
}
```

## 故障排查

### 问题：应用启动时Playwright初始化失败

**解决方案**：
1. 检查Playwright依赖是否正确安装
2. 查看日志中的具体错误信息
3. 确认系统环境支持运行Chromium

### 问题：在Docker中无法启动浏览器

**解决方案**：
1. 使用无头模式：修改 `.setHeadless(true)`
2. 确保Docker镜像包含浏览器运行所需的依赖

### 问题：多线程环境下出现异常

**解决方案**：
1. 为每个线程创建新的Page实例
2. 使用Context的newPage()方法：
   ```java
   Page newPage = playwrightManager.getDesktopContext().newPage();
   ```

## 最佳实践

1. **单页面操作**：尽量复用同一个Page对象进行连续操作
2. **异常处理**：始终使用try-catch包裹Playwright操作
3. **资源清理**：如创建了额外的Page实例，使用后及时关闭
4. **超时设置**：根据实际网络情况调整超时时间

## 示例：完整的爬虫Service

```java
@Service
@Slf4j
public class WebScraperService {
    private final PlaywrightManager playwrightManager;

    public WebScraperService(PlaywrightManager playwrightManager) {
        this.playwrightManager = playwrightManager;
    }

    public String scrapeJobInfo(String url) {
        try {
            Page page = playwrightManager.getDesktopPage();

            // 导航到目标页面
            page.navigate(url);

            // 等待页面加载
            page.waitForLoadState();

            // 提取信息
            String title = page.locator("h1.job-title").textContent();
            String company = page.locator(".company-name").textContent();
            String salary = page.locator(".salary").textContent();

            log.info("成功抓取岗位信息: {} - {} - {}", title, company, salary);

            return String.format("%s | %s | %s", title, company, salary);
        } catch (Exception e) {
            log.error("抓取岗位信息失败: {}", url, e);
            return null;
        }
    }
}
```
