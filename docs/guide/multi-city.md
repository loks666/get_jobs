# 🌏 多城市投递

让 Boss直聘、51job、智联招聘同时投递多个城市（例如「重庆 + 成都」），而不是一次只能选一座城。

---

## 为什么会有这个功能

原版三个平台的城市都是**单选**。对于跨省求职（如「既投重庆也投四川」）的人来说只能二选一，或者手动改城市反复跑任务。

实际上作者在设计 Boss 时已经把多城市写进 worker 层了，只是被保存逻辑和前端控件挡住了。本次改造就是把这条链路彻底打通，并把 51job、智联也对齐。

## 三个平台的实现差异

三个平台的「多城市」实现路径**完全不同**，这里是最关键的背景知识：

### Boss直聘：Worker 层本来就支持

`Boss.execute()` 原本就存在按城市的外层循环：

```java
public int execute() {
    for (String cityCode : config.getCityCode()) {
        ...
        postJobByCity(cityCode);
    }
}
```

而 `BossConfig.cityCode` 也早就声明为 `List<String>`，`BossService` 读取配置时同样用 `parseListString` 解析列表。

**唯一的卡点**在 `BossConfigController.updateConfig()`：保存时调用了 `normalizeCityToName()`，把列表压成了单个城市名。放开这一处即通。

### 51job：后端完全不用改

`Job51.buildSearchUrl()` 用的是列表参数拼接：

```java
url.append(JobUtils.appendListParam("jobArea", config.getJobArea()));
// -> &jobArea=060000,090200
```

51job 的 URL 本身就支持用逗号分隔多个地区一次搜完。因此**只改前端控件**即可，而且多城市几乎不增加耗时。

### 智联招聘：唯一需要真改的

智联的城市写死在 URL 路径里（`sou.zhaopin.com/jl{城市码}/p1`），原实现是单值：

```java
private String buildBaseUrl(int pageNum) {
    url.append("jl").append(config.getCityCode()).append("/");
}
```

改造内容：

- `ZhilianConfig.cityCode` 由 `String` 改为 `List<String>`
- `ZhilianService.loadZhilianConfig()` 重写，支持解析中文名列表 / 代码列表
- 新增 `toBracketListString()`、`normalizeCityListToNames()`
- `ZhilianController.updateConfig()` 保存时统一转为中文名列表
- `ZhiLian.execute()` 增加**城市外层循环**，`buildBaseUrl(pageNum, cityCode)` 接收城市参数

::: tip Boss 与智联是「串行」跑
Boss、智联会**逐个城市**完整执行一遍所有关键词；51job 是一次搜索命中所有城市。这是耗时差异的根源。
:::

## 怎么使用

### 方式一：Web 界面

打开 http://localhost:6866 的对应平台配置页，城市控件已改为**可多选的下拉（带搜索框）**：

- 点击展开，输入关键字（如「成都」）快速过滤
- 勾选多个城市，按钮上会以「重庆，成都」的形式显示已选项
- 右上角「清空」可一键取消选择

::: warning Boss 的城市有 375 个
Boss 的城市库是全国城市级的，没有搜索框根本没法用 —— 这也是新写 `CityMultiSelect` 组件（而非复用页面原有的 `MultiSelect`）的原因。
:::

### 方式二：命令行脚本

```bash
# 设置 Boss 的投递城市
python scripts/set_cities.py boss 重庆 成都

# 设置 51job
python scripts/set_cities.py 51job 重庆 成都

# 设置智联
python scripts/set_cities.py zhilian 重庆 成都

# 不带城市参数 = 只查看当前配置
python scripts/set_cities.py boss
```

脚本会先从后端拉取选项表做名称→编码映射，写不存在的城市会直接报错提示。

### 城市选项覆盖范围

| 平台 | 城市数量 | 川渝可选城市 |
|---|---|---|
| **Boss直聘** | 375（全国城市级） | 重庆、成都、绵阳、德阳、泸州、宜宾、南充… **全覆盖** |
| 51job | 26 | 仅 重庆、成都 |
| 智联招聘 | 42 | 仅 重庆、成都 |
| 猎聘 | 14 | 仅 重庆、成都（**未参与本次改造**） |

::: warning 想投泸州/宜宾，只能靠 Boss
51job 与智联的城市字典里没有宜宾、泸州这类地级市。要在四川全省铺开，主要得依赖 Boss。
:::

## 需要注意的代价

**1. 耗时是按城市数翻倍的。**

Boss / 智联的执行逻辑是「每个城市 × 每个关键词」跑完整流程。选 1 个城市与 4 个城市，任务时长大约就是 **4 倍**。51job 例外，多城市几乎不增加耗时。

**2. 每日上限不会因为多城市而放宽。**

Boss 每天的打招呼次数、智联的投递上限是账号级别的。城市选多了，可能第一个城市就用掉大部分额度。建议先用 2~3 个城市观察效果。

**3. 「不限」与其它城市互斥。**

选择「不限 / 0」会清空其它已选城市 —— 因为「不筛选城市」与其它具体城市同时存在没有意义，反而会多跑若干次等价的搜索。

## 如何确认多城市真的生效

投递时看后端日志即可，三个 worker 都加了城市日志：

```
========== 开始投递城市: 101040100 ==========
...
========== 开始投递城市: 101270100 ==========
```

- **Boss / 智联**：每个城市都会打印一行开始日志；城市数 > 1 时会先打印本次城市总数
- **51job**：搜索时打印本次的城市区域列表，例如 `本次搜索的城市区域: [060000, 090200]`

## 本次改动的文件清单

**后端**

| 文件 | 改动 |
|---|---|
| `application/controller/BossConfigController.java` | 城市从保存单值改为保存列表 |
| `application/controller/ZhilianController.java` | 保存时把城市代码列表统一转成中文名列表 |
| `application/service/ZhilianService.java` | 新增 `toBracketListString` / `normalizeCityListToNames`；重写城市解析支持多值 |
| `worker/boss/Boss.java` | 增加城市日志；空列表保护 |
| `worker/job51/Job51.java` | 增加城市区域日志 |
| `worker/zhilian/ZhiLian.java` | `execute()` 增加城市外层循环；`buildBaseUrl` 接收城市参数 |
| `worker/zhilian/ZhilianConfig.java` | `cityCode` 由 `String` 改为 `List<String>` |

**前端**

| 文件 | 改动 |
|---|---|
| `app/components/CityMultiSelect.tsx` | **新增**，带搜索框的多选下拉组件 |
| `app/boss/page.tsx` | 城市控件改为 CityMultiSelect；保存时提交所选城市列表 |
| `app/51job/page.tsx` | 同上（保留「手动输入城市码」模式） |
| `app/zhilian/page.tsx` | 同上，并新增城市列表 ↔ 编码的映射回填 |

**配套脚本**

| 文件 | 用途 |
|---|---|
| `scripts/set_cities.py` | 命令行批量设置各平台投递城市 |
| `scripts/test_webhook.py` | 企业微信 Webhook 连通性测试（与本功能无关，顺带加入） |

## 数据库存储格式

配置统一以「**中文名称的括号列表**」落库，与其它多选字段保持一致：

```
boss_config.city_code      ->  [重庆,成都]
job51_config.job_area      ->  ["重庆", "成都"]
zhilian_config.city_code   ->  [重庆,成都]
```

读取时由各平台 Service 把中文名映射回平台自己的城市编码。
