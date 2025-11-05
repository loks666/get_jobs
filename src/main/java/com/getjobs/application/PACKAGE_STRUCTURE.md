# GetJobs 应用包结构

## 新的包结构（重构后）

```
com.getjobs
├── GetJobsApplication.java        # Spring Boot 启动类（主入口）
│
├── application/                    # 应用层
│   ├── controller/                # 控制器层 (REST API)
│   │   ├── AiConfigController.java   # AI配置控制器
│   │   ├── BossController.java       # Boss直聘控制器
│   │   ├── ConfigController.java     # 配置控制器
│   │   └── HealthController.java     # 健康检查控制器
│   │
│   ├── service/                   # 业务服务层
│   │   ├── AiConfigService.java  # AI配置服务
│   │   ├── BossService.java      # Boss直聘服务
│   │   ├── ConfigService.java    # 配置服务
│   │   └── CookieService.java    # Cookie服务
│   │
│   ├── mapper/                    # 数据访问层 (MyBatis)
│   │   ├─��� AiMapper.java         # AI配置Mapper
│   │   ├── ConfigMapper.java     # 配置Mapper
│   │   └── CookieMapper.java     # CookieMapper
│   │
│   ├── entity/                    # 实体类
│   │   ├── AiEntity.java         # AI配置实体
│   │   ├── ConfigEntity.java     # 配置实体
│   │   └── CookieEntity.java     # Cookie实体
│   │
│   └── config/                    # 配置类
│       ├── AsyncConfig.java      # 异步配置
│       └── CorsConfig.java       # 跨域配置
│
└── worker/                         # Worker层
    ├── manager/                    # 管理器层
    │   ├── PlaywrightManager.java  # Playwright实例管理器（Spring Bean）
    │   └── README.md               # 使用文档
    │
    ├── boss/                       # Boss直聘相关
    ├── liepin/                     # 猎聘相关
    ├── ai/                         # AI相关
    └── utils/                      # 工具类
```

## 旧包结构 (已删除)

- ~~application/api/~~           → application/controller/
- ~~application/service/~~        → application/service/ (已合并)
- ~~application/domain/service/~~ → application/service/ (已合并)
- ~~application/infra/mapper/~~   → application/mapper/
- ~~application/domain/entity/~~  → application/entity/
- ~~application/infra/config/~~   → application/config/
- ~~application/GetJobsApplication.java~~ → GetJobsApplication.java (移至com.getjobs包)

## 重构内容

### 1. 包名更新
- `com.getjobs.application.api` → `com.getjobs.application.controller`
- `com.getjobs.application.domain.service` + `com.getjobs.application.service` → `com.getjobs.application.service`
- `com.getjobs.application.infra.mapper` → `com.getjobs.application.mapper`
- `com.getjobs.application.domain.entity` → `com.getjobs.application.entity`
- `com.getjobs.application.infra.config` → `com.getjobs.application.config`
- `com.getjobs.application.GetJobsApplication` → `com.getjobs.GetJobsApplication` (主入口上移至顶层包)

### 2. 主要修改
- 所有文件的 package 声明已更新
- 所有 import 语句已更新
- GetJobsApplication.java 移至 com.getjobs 包，作为整个项目的主入口
- GetJobsApplication.java 的 @MapperScan 注解保持为 "com.getjobs.application.mapper"
- GetJobsApplication.java 的 @SpringBootApplication 的 scanBasePackages 保持为 "com.getjobs"

### 3. 优势
- ✅ 清晰的分层架构
- ✅ 统一的包命名规范
- ✅ 更容易理解和维护
- ✅ 符合标准的 Spring Boot 项目结构
- ✅ 减少嵌套层级，提高可读性
- ✅ 主入口位于顶层包，便于后续模块扩展（如worker包的重构）

## Worker层重构（新增）

### 1. PlaywrightManager
- **位置**：`com.getjobs.worker.manager.PlaywrightManager`
- **类型**：Spring管理的单例Bean（@Component）
- **功能**：
  - 在应用启动时自动初始化Playwright实例（@PostConstruct）
  - 在应用关闭时自动释放资源（@PreDestroy）
  - 自动检测屏幕尺寸并适配浏览器视口
  - 提供桌面和移动设备浏览器实例
  - 支持依赖注入到任何Spring Bean

### 2. 主要特性
- ✅ **自动启动**：无需手动调用init()，Spring Boot启动时自动初始化
- ✅ **自适应屏幕**：自动检测系统屏幕尺寸（如1920x1080）并适配
- ✅ **资源管理**：应用关闭时自动释放所有Playwright资源
- ✅ **依赖注入**：可在Service、Controller等任何地方注入使用
- ✅ **双模式支持**：同时支持桌面和移动设备浏览器

### 3. 使用示例
```java
@Service
public class JobScraperService {
    private final PlaywrightManager playwrightManager;

    public JobScraperService(PlaywrightManager playwrightManager) {
        this.playwrightManager = playwrightManager;
    }

    public void scrapeJobs() {
        Page page = playwrightManager.getDesktopPage();
        page.navigate("https://www.zhipin.com");
        // 无需手动关闭，Spring会自动管理
    }
}
```

### 4. API测试接口
- `GET /api/playwright/status` - 查看Playwright状态
- `GET /api/playwright/test-navigate` - 测试导航功能

详细文档：`src/main/java/com/getjobs/worker/manager/README.md`

## 编译验证

重构后项目编译成功 ✓

```bash
./gradlew clean build -x test
BUILD SUCCESSFUL in 3s
```

