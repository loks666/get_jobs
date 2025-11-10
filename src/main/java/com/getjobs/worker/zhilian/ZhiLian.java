package com.getjobs.worker.zhilian;

import com.getjobs.worker.utils.Job;
import com.getjobs.worker.utils.JobUtils;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 * 智联招聘自动投递 - Playwright版本
 */
@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class ZhiLian {

    @Setter
    private Page page;

    @Setter
    private ZhilianConfig config;

    @Setter
    private ProgressCallback progressCallback;

    @Setter
    private Supplier<Boolean> shouldStopCallback;

    private final List<Job> resultList = new ArrayList<>();
    private boolean isLimit = false;
    private int maxPage = 500;

    private static final String HOME_URL = "https://sou.zhaopin.com/?";

    /**
     * 进度回调接口
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void accept(String message, Integer current, Integer total);
    }

    /**
     * 准备工作：加载配置、初始化数据
     */
    public void prepare() {
        log.info("智联招聘准备工作开始...");
        resultList.clear();
        isLimit = false;
        log.info("智联招聘准备工作完成");
    }

    /**
     * 执行投递任务
     * @return 投递数量
     */
    public int execute() {
        log.info("智联招聘投递任务开始...");
        long startTime = System.currentTimeMillis();

        try {
            // 遍历所有关键词进行投递
            for (String keyword : config.getKeywords()) {
                if (shouldStop() || isLimit) {
                    sendProgress("用户取消投递或已达上限", null, null);
                    break;
                }

                String searchUrl = buildSearchUrl(keyword, 1);
                deliverByKeyword(keyword, searchUrl);
            }

            long duration = System.currentTimeMillis() - startTime;
            String message = String.format("智联招聘投递完成，共投递%d个岗位，用时%s",
                resultList.size(), formatDuration(duration));
            log.info(message);
            sendProgress(message, null, null);

            if (!resultList.isEmpty()) {
                log.info("新投递公司如下:");
                resultList.forEach(job -> log.info(job.toString()));
            } else {
                log.info("未投递新的岗位...");
            }

        } catch (Exception e) {
            log.error("智联招聘投递过程出现异常", e);
            sendProgress("投递出现异常: " + e.getMessage(), null, null);
        }

        return resultList.size();
    }

    /**
     * 按关键词投递
     */
    private void deliverByKeyword(String keyword, String searchUrl) {
        if (isLimit) {
            return;
        }

        try {
            log.info("开始投递关键词: {}", keyword);
            sendProgress("正在搜索关键词: " + keyword, null, null);

            // 导航到搜索页面
            page.navigate(searchUrl);
            PlaywrightUtil.sleep(2);

            // 等待岗位列表加载
            try {
                page.waitForSelector("//div[contains(@class, 'joblist-box__item')]",
                    new Page.WaitForSelectorOptions().setTimeout(10_000));
            } catch (Exception e) {
                log.warn("等待岗位列表超时，跳过当前关键词");
                return;
            }

            // 设置最大页数
            setMaxPages();

            // 遍历所有页面
            for (int pageNum = 1; pageNum <= maxPage; pageNum++) {
                if (shouldStop() || isLimit) {
                    sendProgress("用户取消投递或已达上限", null, null);
                    return;
                }

                log.info("开始投递【{}】关键词，第【{}】页...", keyword, pageNum);
                sendProgress(String.format("正在投递第%d页", pageNum), pageNum, maxPage);

                // 如果不是第一页，需要导航到该页
                if (pageNum > 1) {
                    page.navigate(buildSearchUrl(keyword, pageNum));
                    PlaywrightUtil.sleep(2);
                }

                // 等待岗位列表出现
                try {
                    page.waitForSelector("//div[@class='positionlist']",
                        new Page.WaitForSelectorOptions().setTimeout(10_000));
                } catch (Exception e) {
                    log.warn("等待岗位列表失败，刷新页面重试");
                    page.reload();
                    PlaywrightUtil.sleep(1);
                }

                // 投递当前页面
                if (!deliverCurrentPage(keyword)) {
                    break;
                }

                PlaywrightUtil.sleep(2);
            }

            log.info("关键词【{}】投递完成", keyword);
        } catch (Exception e) {
            log.error("投递关键词【{}】时出现异常", keyword, e);
        }
    }

    /**
     * 投递当前页面的所有职位
     * @return 是否继续投递下一页
     */
    private boolean deliverCurrentPage(String keyword) {
        try {
            // 点击全选
            try {
                Locator allSelectCheckbox = page.locator("//i[@class='betch__checkall__checkbox']");
                if (allSelectCheckbox.count() == 0) {
                    log.info("没有全选按钮，跳过当前页");
                    return false;
                }
                allSelectCheckbox.click();
                PlaywrightUtil.sleep(1);
            } catch (Exception e) {
                log.warn("点击全选失败: {}", e.getMessage());
                return false;
            }

            // 点击批量投递按钮
            try {
                Locator submitButton = page.locator("//button[@class='betch__button']");
                if (submitButton.count() == 0) {
                    log.warn("未找到批量投递按钮");
                    return false;
                }
                submitButton.click();

                // 检查是否达到投递上限
                if (checkIsLimit()) {
                    return false;
                }

                PlaywrightUtil.sleep(2);
            } catch (Exception e) {
                log.error("点击批量投递按钮失败: {}", e.getMessage());
                return false;
            }

            // 处理投递弹窗（在新打开的页面中）
            handleDeliveryDialog();

            return true;
        } catch (Exception e) {
            log.error("投递当前页面失败", e);
            return false;
        }
    }

    /**
     * 处理投递弹窗
     */
    private void handleDeliveryDialog() {
        try {
            // 获取所有页面
            List<Page> pages = page.context().pages();
            if (pages.size() < 2) {
                log.warn("未检测到投递弹窗页面");
                return;
            }

            // 切换到最新打开的页面（投递弹窗）
            Page dialogPage = pages.get(pages.size() - 1);

            try {
                // 检查投递结果
                Locator deliverResult = dialogPage.locator("//div[@class='deliver-dialog']");
                if (deliverResult.count() > 0) {
                    String text = deliverResult.textContent();
                    if (text != null && text.contains("申请成功")) {
                        log.info("岗位申请成功！");
                    }
                }
            } catch (Exception e) {
                log.debug("读取投递结果失败: {}", e.getMessage());
            }

            // 关闭弹窗
            try {
                Locator closeButton = dialogPage.locator("//img[@title='close-icon']");
                if (closeButton.count() > 0) {
                    closeButton.click();
                    PlaywrightUtil.sleep(1);
                }
            } catch (Exception e) {
                log.debug("关闭投递弹窗失败: {}", e.getMessage());
                if (checkIsLimit()) {
                    return;
                }
            }

            // 投递相似职位
            deliverSimilarJobs(dialogPage);

            // 关闭弹窗页面
            try {
                dialogPage.close();
            } catch (Exception e) {
                log.debug("关闭弹窗页面失败: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("处理投递弹窗失败", e);
        }
    }

    /**
     * 投递相似职位
     */
    private void deliverSimilarJobs(Page dialogPage) {
        try {
            // 全选相似职位
            Locator selectAllCheckbox = dialogPage.locator("//div[contains(@class, 'applied-select-all')]//input");
            if (selectAllCheckbox.count() > 0 && !selectAllCheckbox.isChecked()) {
                selectAllCheckbox.click();
                PlaywrightUtil.sleep(1);
            }

            // 获取相似职位列表
            Locator jobs = dialogPage.locator("//div[@class='recommend-job']");
            int jobCount = jobs.count();

            if (jobCount == 0) {
                log.info("没有匹配到相似职位");
                return;
            }

            // 记录相似职位信息
            for (int i = 0; i < jobCount; i++) {
                try {
                    Locator jobElement = jobs.nth(i);
                    String jobName = safeGetText(jobElement, ".//*[contains(@class, 'recommend-job__position')]");
                    String salary = safeGetText(jobElement, ".//span[@class='recommend-job__demand__salary']");
                    String years = safeGetText(jobElement, ".//span[@class='recommend-job__demand__experience']").replace("\n", " ");
                    String education = safeGetText(jobElement, ".//span[@class='recommend-job__demand__educational']").replace("\n", " ");
                    String companyName = safeGetText(jobElement, ".//*[contains(@class, 'recommend-job__cname')]");
                    String companyTag = safeGetText(jobElement, ".//*[contains(@class, 'recommend-job__demand__cinfo')]").replace("\n", " ");

                    Job job = new Job();
                    job.setJobName(jobName);
                    job.setSalary(salary);
                    job.setCompanyTag(companyTag);
                    job.setCompanyName(companyName);
                    job.setJobInfo(years + "·" + education);

                    log.info("投递【{}】公司【{}】岗位，薪资【{}】，要求【{}·{}】，规模【{}】",
                        companyName, jobName, salary, years, education, companyTag);
                    resultList.add(job);
                } catch (Exception e) {
                    log.debug("记录相似职位信息失败: {}", e.getMessage());
                }
            }

            // 点击投递按钮
            Locator postButton = dialogPage.locator("//div[contains(@class, 'applied-select-all')]//button");
            if (postButton.count() > 0) {
                postButton.click();
                PlaywrightUtil.sleep(2);
                log.info("相似职位投递成功！");
            }

        } catch (Exception e) {
            log.error("投递相似职位异常: {}", e.getMessage());
        }
    }

    /**
     * 检查是否达到投递上限
     */
    private boolean checkIsLimit() {
        try {
            PlaywrightUtil.sleep(1);
            Locator result = page.locator("//div[@class='a-job-apply-workflow']");
            if (result.count() > 0) {
                String text = result.textContent();
                if (text != null && text.contains("达到上限")) {
                    log.info("今日投递已达上限！");
                    isLimit = true;
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 设置最大页数
     */
    private void setMaxPages() {
        try {
            // 滚动到底部
            page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
            PlaywrightUtil.sleep(1);

            Locator pageInput = page.locator(".soupager__pagebox__goinp");
            if (pageInput.count() > 0) {
                pageInput.fill("");
                pageInput.fill("99999");

                // 获取输入框的实际值
                String value = pageInput.inputValue();
                maxPage = Integer.parseInt(value);
                log.info("设置最大页数：{}", maxPage);

                // 移动到首页位置
                Locator home = page.locator("//li[@class='listsort__item']");
                if (home.count() > 0) {
                    home.first().scrollIntoViewIfNeeded();
                }
            }
        } catch (Exception e) {
            log.info("setMaxPages@设置最大页数异常！");
            log.info("设置默认最大页数50，如有需要请自行调整...");
            maxPage = 50;
        }
    }

    /**
     * 构建搜索URL
     */
    private String buildSearchUrl(String keyword, int pageNum) {
        StringBuilder url = new StringBuilder(HOME_URL);
        url.append(JobUtils.appendParam("jl", config.getCityCode()));
        url.append(JobUtils.appendParam("kw", keyword));
        url.append(JobUtils.appendParam("sl", config.getSalary()));
        url.append("&p=").append(pageNum);
        return url.toString();
    }

    /**
     * 安全获取文本内容
     */
    private String safeGetText(Locator parent, String selector) {
        try {
            Locator element = parent.locator(selector);
            if (element.count() > 0) {
                return element.textContent();
            }
        } catch (Exception e) {
            log.debug("获取文本失败: {}", e.getMessage());
        }
        return "";
    }

    /**
     * 格式化时长
     */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d分钟%d秒", minutes, seconds % 60);
        } else {
            return String.format("%d秒", seconds);
        }
    }

    /**
     * 发送进度消息
     */
    private void sendProgress(String message, Integer current, Integer total) {
        if (progressCallback != null) {
            progressCallback.accept(message, current, total);
        }
    }

    /**
     * 检查是否应该停止
     */
    private boolean shouldStop() {
        return shouldStopCallback != null && shouldStopCallback.get();
    }
}
