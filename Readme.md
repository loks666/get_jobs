<h1 align="center"> get-jobs</h1>
<div align="center">
    💼自动投简历(Boss直聘、猎聘、拉勾、51job)
</div><br>

- 本项目受此启发:https://github.com/BeammNotFound/get-jobs-51job , 感谢大佬，让我们将爱传递下去~

## 如何使用？

#### 第一步：配置Chrome(需和diver版本一致)

> driver目前已放进根目录，版本号是：122.0.6261.112，Chrome需要更新到最新版本。  
> 若后面发生升级，请自行下载对应版本的driver
- driver下载链接：https://googlechromelabs.github.io/chrome-for-testing

> 例：你的路径是：**C:/Program Files/Google/Chrome/Application/chrome.exe** , 则修改 **SeleniumUtil** 的 **getChromeDriver( )**
> 代码

```
options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
```



#### 第二步：修改代码(一般默认即可)

- 通用配置
    - **page** ：从第几页开始投递，page不能小于1<br>
    - **maxPage**：投递到第几页<br>
    - **EnableNotifications**：是否开启Telegram机器人通知
    - 日志文件在 **target/logs** 目录下，所有日志都会输出在以运行日期结尾的日志文件中
    - **cookie登录**: 登录后会在运行路径下保存一个json文件，下次运行会自动读取这个文件，无需再次登录(目前仅支持Boss)

- boss直聘([SubmitBoss.java](src%2Fmain%2Fjava%2Fboss%2FSubmitBoss.java))

   ```
   keyword = “Java”; // 岗位关键词
   blackCompanies = List.of("复深蓝"); // 公司黑名单，多个用逗号分隔
   blackRecruiters = List.of("猎头"); // 排除招聘人员，比如猎头
   blackJobs = List.of("外包", "外派"); // 排除岗位，比如外包，外派
   sayHi = "您好，我上班不要工资而且可以给公司钱！"; // 打招呼语，自行设置，需要关闭自动打招呼
   ```

- 51job([SubmitJob.java](src%2Fmain%2Fjava%2Fjob51%2FSubmitJob.java))

  ```
  jobArea=020000 //上海地区码，可以在51job选择地区后点击搜索，在地址栏寻找自己的目标地区码
  keywords:关键词 //通过keywords.get(?)使用
  scanLogin() //扫码登录(默认方式)
  inputLogin() //密码登录(需要手动过验证)
  ```
- 拉勾([SubmitLagou.java](src%2Fmain%2Fjava%2Flagou%2FSubmitLagou.java))

   ```
   拉勾直接使用的是微信扫码登录，运行后直接扫码即可，开箱通用
   但是拉勾由于反爬机制较为严重，代码中嵌套了大量的sleep，导致效率较慢
  这边建议拉勾的脚本运行一段时间后差不多就行了，配合手动在app或者微信小程序投递简历效果更佳！
   ```

- 猎聘([SubmitLiepin.java](src%2Fmain%2Fjava%2Fliepin%2FSubmitLiepin.java))

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
****
## 注意事项
- boss出现访问异常：使用selenium在登录成功后boss会进行无限重定向导致账号ip异常(较低几率)
    - 解决方案：一般3秒后如果代码没有打开新的界面请关闭脚本并重新运行，若已出现异常，则手动过验证后重新运行即可。


## 免责声名

为避免别有用心之人利用本代码进行违法活动，特此声明：

- 本项目完全开源，但将项目用于其他用途目的均与本项目无关，由此引发的一切法律责任由使用者自行承担。
- 现在这个大环境下就算有脚本的帮助可能机会也很少，但总归要试一试，不是么？
- 作者已经失业很久了，真的很希望有个班上，开源这个项目也希望能帮到真正需要它的人
- 最后，希望各位永远不需要本项目的帮助，但别忘了star哟！

## 请我喝杯咖啡☕️

<img src="./src/main/resources/images/aliPay.jpg" alt="" width="300"> <img src="./src/main/resources/images/wechatPay.jpg" alt="" width="300">

## 联系方式📧

- V2VDaGF0OkFpckVsaWF1azk1Mjcs6K+35aSH5rOo77ya5pq06aOO6Zuo5bCx6KaB5p2l5LqG


