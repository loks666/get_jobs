# GetJobs 应用包结构

## 新的包结构（重构后）

```
com.getjobs.application
├── controller/                     # 控制器层 (REST API)
│   ├── AiConfigController.java   # AI配置控制器
│   ├── BossController.java       # Boss直聘控制器
│   ├── ConfigController.java     # 配置控制器
│   └── HealthController.java     # 健康检查控制器
│
├── service/                        # 业务服务层
│   ├── AiConfigService.java      # AI配置服务
│   ├── BossService.java          # Boss直聘服务
│   ├── ConfigService.java        # 配置服务
│   └── CookieService.java        # Cookie服务
│
├── mapper/                         # 数据访问层 (MyBatis)
│   ├── AiMapper.java             # AI配置Mapper
│   ├── ConfigMapper.java         # 配置Mapper
│   └── CookieMapper.java         # CookieMapper
│
├── entity/                         # 实体类
│   ├── AiEntity.java             # AI配置实体
│   ├── ConfigEntity.java         # 配置实体
│   └── CookieEntity.java         # Cookie实体
│
├── config/                         # 配置类
│   ├── AsyncConfig.java          # 异步配置
│   └── CorsConfig.java           # 跨域配置
│
└── GetJobsApplication.java        # Spring Boot 启动类
```

## 旧包结构 (已删除)

- ~~application/api/~~           → controller/
- ~~application/service/~~        → service/ (已合并)
- ~~application/domain/service/~~ → service/ (已合并)
- ~~application/infra/mapper/~~   → mapper/
- ~~application/domain/entity/~~  → entity/
- ~~application/infra/config/~~   → config/

## 重构内容

### 1. 包名更新
- `com.getjobs.application.api` → `com.getjobs.application.controller`
- `com.getjobs.application.domain.service` + `com.getjobs.application.service` → `com.getjobs.application.service`
- `com.getjobs.application.infra.mapper` → `com.getjobs.application.mapper`
- `com.getjobs.application.domain.entity` → `com.getjobs.application.entity`
- `com.getjobs.application.infra.config` → `com.getjobs.application.config`

### 2. 主要修改
- 所有文件的 package 声明已更新
- 所有 import 语句已更新
- GetJobsApplication.java 的 @MapperScan 注解已更新为 "com.getjobs.application.mapper"

### 3. 优势
- ✅ 清晰的分层架构
- ✅ 统一的包命名规范
- ✅ 更容易理解和维护
- ✅ 符合标准的 Spring Boot 项目结构
- ✅ 减少嵌套层级，提高可读性

## 编译验证

重构后项目编译成功 ✓

```bash
./gradlew clean build -x test
BUILD SUCCESSFUL in 3s
```

