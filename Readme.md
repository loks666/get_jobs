<h1 align="center"> get-jobs-51job</h1>
<div align="center">
    💼在前程无忧自动投递简历
</div>

## 如何使用
- 该项目使用selenium自动化测试框架，使用教程如下：
#### 第一步：配置Chrome(需和diver版本一致)
> driver下载链接：https://googlechromelabs.github.io/chrome-for-testing  
> driver目前已放进根目录，版本号是：122.0.6261.112，如果后面发生升级，请自行下载对应版本的driver  
> 配置chrome路径，如果你的路径是：C:/Program Files/Google/Chrome/Application/chrome.exe,则修改下面的代码
```
options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
```

#### 第二步：修改代码(一般默认即可)

打开 /src/main/java/ResumeSubmission.java <br>
**page** ：从第几页开始投递，page不能小于1<br>
**maxPage**：投递到第几页<br>
**EnableNotifications**：是否开启Telegram机器人通知  
#### 修改TelegramBot通知代码（没有代理无法使用）
打开 /src/main/java/utils/TelegramNotificationBot.java <br>
**TELEGRAM_API_TOKEN** ：你的机器人的token <br>
**CHAT_ID**：你的机器人的chat_id <br>
#### 最后一步：运行代码
默认使用扫码登录：scanLogin  
也可以使用密码登录：inputLogin(需要手动过验证)
<br>
****

#### 其他的有需要可以改，放开注释即可，不改不影响运行
-  搜索地区设置
```
jobArea=020000
```
> 020000是上海地区的码，可以在51job选择地区后在地址栏寻找自己的目标地区的地区码是什么，做相应修改即可
-  搜索关键词设置
```
List<String> keywords = Arrays.asList("java", "python", "go", "golang", "大模型");
```
> 这是默认的关键词列表，可以添加或修改。
```
resume(String.format(baseUrl, keywords.get(0)));
```
> 以上代码会设置关键词为Java  
> 由于51的反爬机制，如果是使用循环的方式，可能会被封ip，所以目前使用keywords.get(?)的方式
-  推送Telegram消息
```
new TelegramNotificationBot().sendMessageWithList(message, returnList, "前程无忧投递");
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

> 希望能够在现在的大环境下帮助你找到一份满意的工作

## 请我喝杯咖啡☕️
<img src="./src/main/resources/images/aliPay.jpg" alt="" width="300"> <img src="./src/main/resources/images/wechatPay.jpg" alt="" width="300">


