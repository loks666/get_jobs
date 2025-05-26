# BossConfig 单例模式使用说明

## 概述
`BossConfig` 类已经优化为单例模式，确保整个应用程序生命周期中只有一个配置实例。

## 主要改动

### 1. 单例实现
- 使用双重检查锁定（Double-Check Locking）确保线程安全
- 添加了 `volatile` 关键字防止指令重排
- 私有构造函数防止外部实例化

### 2. API 变更

#### 旧用法（已废弃）
```java
// 每次调用都会创建新实例
BossConfig config = BossConfig.init();
```

#### 新用法（推荐）
```java
// 获取单例实例
BossConfig config = BossConfig.getInstance();
```

## 使用示例

### 获取配置实例
```java
public class ExampleService {
    public void doSomething() {
        // 获取单例配置
        BossConfig config = BossConfig.getInstance();
        
        // 使用配置
        String sayHi = config.getSayHi();
        List<String> keywords = config.getKeywords();
        // ... 其他操作
    }
}
```

### 重新加载配置
当配置文件更新时，可以调用 `reload()` 方法重新加载配置：

```java
// 重新加载配置文件
BossConfig.reload();

// 获取更新后的配置
BossConfig config = BossConfig.getInstance();
```

## 优势

1. **性能优化**：避免重复创建实例，减少内存开销
2. **配置一致性**：确保整个应用使用同一份配置
3. **线程安全**：使用双重检查锁定确保多线程环境下的安全性
4. **延迟加载**：首次调用 `getInstance()` 时才加载配置

## 注意事项

1. 如果需要在运行时更新配置，请使用 `reload()` 方法
2. 不要尝试通过反射或其他方式创建新的 `BossConfig` 实例
3. 在多线程环境中，`getInstance()` 方法是线程安全的 