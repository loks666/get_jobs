<h1 align="center"> get-jobs-51job</h1>
<div align="center">
    💼自动投简历(Boss直聘、猎聘、拉勾、51job)
</div>

- 本项目受此启发:https://github.com/BeammNotFound/get-jobs-51job
- 感谢大佬，让我们传递一份爱~

## 如何使用？

#### 第一步：配置Chrome(需和diver版本一致)

> driver目前已放进根目录，版本号是：122.0.6261.112，如果后面发生升级，请自行下载对应版本的driver  
> 如果你的路径是：C:/Program Files/Google/Chrome/Application/chrome.exe,则修改 **SeleniumUtil** 的 **getChromeDriver** 代码

```
options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
```

- driver下载链接：https://googlechromelabs.github.io/chrome-for-testing

#### 第二步：修改代码(一般默认即可)

- 通用配置
    - **page** ：从第几页开始投递，page不能小于1<br>
    - **maxPage**：投递到第几页<br>
    - **EnableNotifications**：是否开启Telegram机器人通知

- boss直聘([SubmitBoss.java](src%2Fmain%2Fjava%2Fboss%2FSubmitBoss.java))

   ```
   keyword = “Java”; // 岗位关键词
   blackCompanies = List.of("复深蓝"); // 公司黑名单，多个用逗号分隔
   blackRecruiters = List.of("猎头"); // 排除招聘人员，比如猎头
   blackJobs = List.of("外包", "外派"); // 排除岗位，比如外包，外派
   sayHi = "您好，我上班可以给公司钱！"; // 打招呼语，自行设置，需要关闭自动打招呼
   ```

- 51job([SubmitJob.java](src%2Fmain%2Fjava%2Fjob51%2FSubmitJob.java))

  ```
  jobArea=020000 //上海地区码，可以在51job选择地区后点击搜索，在地址栏寻找自己的目标地区码
  keywords:关键词 //通过keywords.get(?)使用
  scanLogin() //扫码登录(默认方式)
  inputLogin() //密码登录(需要手动过验证)
  ```

- 猎聘([SubmitLiepin.java](src%2Fmain%2Fjava%2Fliepin%2FSubmitLiepin.java))

   ```
   正在开发中。。。
   ```

- 拉勾([SubmitLagou.java](src%2Fmain%2Fjava%2Flagou%2FSubmitLagou.java))

   ```
   正在开发中。。。
   ```

#### 最后一步：运行代码

- 直接运行你想要投递平台的下的代码即可

****

#### 其他的有需要可以改，放开注释即可，不改不影响运行

- TelegramBot机器人通知（可选，需要代理）
   ```
   TELEGRAM_API_TOKEN: 你的机器人的token
   CHAT_ID: 你的机器人的chat_id
   ```

- 推送Telegram消息
   ```
   new TelegramNotificationBot().sendMessageWithList(message, returnList, "xx平台投递");
   ```

- 将窗口移动到副屏

   ```
   options.addArguments("--window-position=2600,750"); // 将窗口移动到副屏的起始位置
   options.addArguments("--window-size=1600,1000"); // 设置窗口大小以适应副屏分辨率
   ```

## 免责声名

本开源项目（以下简称“本项目”）为自由、开放、共享的非赢利性项目，由开发者（以下简称“我们”）所创建并维护。我们不对使用本项目产生的任何后果承担任何责任。

1. 本项目的代码仅供参考学习，不保证其准确性、完整性或实用性。开发者不承担因使用本项目引发的任何直接或间接损失或损害的法律责任。
2. 本项目中可能包含第三方组件或库，这些组件或库的使用可能受到其他许可证的限制。使用者应该自行了解并遵守相关许可证的规定，并对因使用这些组件或库而引发的任何法律责任自负。

3. 本项目中可能包含链接到其他网站或资源的链接。这些链接仅供参考。
   我们不对这些网站或资源的可用性、准确性、完整性、合法性或任何其他方面的信息负责。使用者应该自行决定是否信任这些链接，并对因使用这些链接而引发的任何法律责任自负。
4. 我们保留随时更改本免责声明的权利。使用者应该定期查看本免责声明，以了解任何变更。如果使用者继续使用本项目，则视为同意遵守新的免责声明。

> 希望能够在现在的大环境下帮助你找到一份满意的工作，别忘了star！

## 请我喝杯咖啡☕️

<img src="./src/main/resources/images/aliPay.jpg" alt="" width="300"> <img src="./src/main/resources/images/wechatPay.jpg" alt="" width="300">


