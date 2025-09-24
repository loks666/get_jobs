package getjobs.modules.zhilian.service;

import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

/**
 * 智联招聘网站元素定位器
 * 用于定位智联招聘网站上的各种元素
 */
@Slf4j
public class ZhilianElementLocators {

    /**
     * 检查登录页是否存在登录元素
     * 通过检查class="login-box clearfix"的div元素以及内部的关键组件来判断是否在登录页面
     * 
     * @param page Playwright页面对象
     * @return true表示存在登录元素（在登录页面），false表示不存在（不在登录页面或已登录）
     */
    public static boolean hasLoginElement(Page page) {
        try {
            // 查找最外层的登录容器：class="login-box clearfix"
            var loginBoxElement = page.locator("div.login-box.clearfix");
            
            if (loginBoxElement.count() == 0) {
                log.info("未找到登录容器元素 div.login-box.clearfix");
                return false;
            }
            
            // 检查内部的智联通行证容器
            var passportContainer = loginBoxElement.locator("#zpPassportWidgetContainer");
            if (passportContainer.count() == 0) {
                log.info("未找到智联通行证容器 #zpPassportWidgetContainer");
                return false;
            }
            
            // 检查是否存在登录表单相关元素
            var loginPanel = passportContainer.locator(".zppp-panel-login-normal");
            if (loginPanel.count() == 0) {
                log.info("未找到登录面板 .zppp-panel-login-normal");
                return false;
            }
            
            // 检查是否存在手机号输入框
            var phoneInput = passportContainer.locator("input[placeholder*='手机号']");
            if (phoneInput.count() == 0) {
                log.info("未找到手机号输入框");
                return false;
            }
            
            // 检查是否存在验证码输入框
            var smsInput = passportContainer.locator("input[placeholder*='短信验证码']");
            if (smsInput.count() == 0) {
                log.info("未找到短信验证码输入框");
                return false;
            }
            
            // 检查是否存在登录/注册按钮
            var loginButton = passportContainer.locator("button:has-text('登录/注册')");
            if (loginButton.count() == 0) {
                log.info("未找到登录/注册按钮");
                return false;
            }
            
            log.info("检测到智联招聘登录页面的所有必要元素");
            return true;
            
        } catch (Exception e) {
            log.error("检查智联招聘登录元素时发生错误: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 判断用户是否已登录
     * 通过检查页面是否不存在登录相关元素来判断登录状态
     * 
     * @param page Playwright页面对象
     * @return true表示已登录，false表示未登录
     */
    public static boolean isUserLoggedIn(Page page) {
        try {
            // 如果存在登录元素，说明未登录
            if (hasLoginElement(page)) {
                return false;
            }
            
            // 可以进一步检查是否存在用户相关的已登录元素
            // 这里需要根据智联招聘已登录后的页面结构来实现
            // 暂时使用简单的逻辑：没有登录元素就认为已登录
            return true;
            
        } catch (Exception e) {
            log.error("检查登录状态时发生错误: {}", e.getMessage());
            // 如果出现异常，默认认为未登录
            return false;
        }
    }
    
    /**
     * 在登录页面输入手机号
     * 
     * @param page        Playwright页面对象
     * @param phoneNumber 手机号码
     * @return true表示输入成功，false表示输入失败
     */
    public static boolean inputPhoneNumber(Page page, String phoneNumber) {
        try {
            // 查找手机号输入框
            var phoneInput = page.locator("input[placeholder*='手机号']");
            
            if (phoneInput.count() == 0) {
                log.error("未找到手机号输入框");
                return false;
            }
            
            // 清空并输入手机号
            phoneInput.clear();
            phoneInput.fill(phoneNumber);
            
            log.info("成功输入手机号: {}", phoneNumber);
            return true;
            
        } catch (Exception e) {
            log.error("输入手机号时发生错误: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 在登录页面输入短信验证码
     * 
     * @param page        Playwright页面对象
     * @param smsCode     短信验证码
     * @return true表示输入成功，false表示输入失败
     */
    public static boolean inputSmsCode(Page page, String smsCode) {
        try {
            // 查找短信验证码输入框
            var smsInput = page.locator("input[placeholder*='短信验证码']");
            
            if (smsInput.count() == 0) {
                log.error("未找到短信验证码输入框");
                return false;
            }
            
            // 清空并输入验证码
            smsInput.clear();
            smsInput.fill(smsCode);
            
            log.info("成功输入短信验证码");
            return true;
            
        } catch (Exception e) {
            log.error("输入短信验证码时发生错误: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 点击获取验证码按钮
     * 
     * @param page Playwright页面对象
     * @return true表示点击成功，false表示点击失败
     */
    public static boolean clickGetSmsCodeButton(Page page) {
        try {
            // 查找获取验证码按钮
            var getSmsButton = page.locator("button:has-text('获取验证码')");
            
            if (getSmsButton.count() == 0) {
                log.error("未找到获取验证码按钮");
                return false;
            }
            
            // 检查按钮是否可点击（非禁用状态）
            if (getSmsButton.isDisabled()) {
                log.error("获取验证码按钮处于禁用状态");
                return false;
            }
            
            // 点击按钮
            getSmsButton.click();
            
            log.info("成功点击获取验证码按钮");
            return true;
            
        } catch (Exception e) {
            log.error("点击获取验证码按钮时发生错误: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 点击登录/注册按钮
     * 
     * @param page Playwright页面对象
     * @return true表示点击成功，false表示点击失败
     */
    public static boolean clickLoginButton(Page page) {
        try {
            // 查找登录/注册按钮
            var loginButton = page.locator("button:has-text('登录/注册')");
            
            if (loginButton.count() == 0) {
                log.error("未找到登录/注册按钮");
                return false;
            }
            
            // 检查按钮是否可点击
            if (loginButton.isDisabled()) {
                log.error("登录/注册按钮处于禁用状态");
                return false;
            }
            
            // 点击按钮
            loginButton.click();
            
            log.info("成功点击登录/注册按钮");
            
            // 等待页面响应
            page.waitForTimeout(1000);
            
            return true;
            
        } catch (Exception e) {
            log.error("点击登录/注册按钮时发生错误: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查是否同意用户协议
     * 
     * @param page Playwright页面对象
     * @return true表示已同意，false表示未同意
     */
    public static boolean isUserAgreementAccepted(Page page) {
        try {
            // 查找用户协议复选框
            var checkbox = page.locator("input.zppp-accept__checkbox[type='checkbox']");
            
            if (checkbox.count() == 0) {
                // 如果没有找到复选框，可能默认已同意
                return true;
            }
            
            return checkbox.isChecked();
            
        } catch (Exception e) {
            log.error("检查用户协议状态时发生错误: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 同意用户协议
     * 
     * @param page Playwright页面对象
     * @return true表示操作成功，false表示操作失败
     */
    public static boolean acceptUserAgreement(Page page) {
        try {
            // 查找用户协议复选框
            var checkbox = page.locator("input.zppp-accept__checkbox[type='checkbox']");
            
            if (checkbox.count() == 0) {
                log.info("未找到用户协议复选框，可能默认已同意");
                return true;
            }
            
            // 如果未选中，则点击选中
            if (!checkbox.isChecked()) {
                checkbox.click();
                log.info("成功同意用户协议");
            } else {
                log.info("用户协议已经同意");
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("同意用户协议时发生错误: {}", e.getMessage());
            return false;
        }
    }
}
