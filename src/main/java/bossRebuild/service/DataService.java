package bossRebuild.service;

import bossRebuild.constants.Constants;
import bossRebuild.constants.Elements;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.SeleniumUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static utils.Constant.*;

public class DataService {
    private static final Logger log = LoggerFactory.getLogger(DataService.class);

    public void loadData(String path, Set<String> blackCompanies, Set<String> blackRecruiters, Set<String> blackJobs) {
        try {
            String json = new String(Files.readAllBytes(Paths.get(path)));
            parseJson(json, blackCompanies, blackRecruiters, blackJobs);
        } catch (IOException e) {
            log.error("读取【{}】数据失败！", path);
        }
    }

    private void parseJson(String json, Set<String> blackCompanies, Set<String> blackRecruiters, Set<String> blackJobs) {
        JSONObject jsonObject = new JSONObject(json);
        blackCompanies.addAll(jsonObject.getJSONArray("blackCompanies").toList().stream().map(Object::toString).collect(Collectors.toSet()));
        blackRecruiters.addAll(jsonObject.getJSONArray("blackRecruiters").toList().stream().map(Object::toString).collect(Collectors.toSet()));
        blackJobs.addAll(jsonObject.getJSONArray("blackJobs").toList().stream().map(Object::toString).collect(Collectors.toSet()));
    }

    public void saveData(String path, Set<String> blackCompanies, Set<String> blackRecruiters, Set<String> blackJobs) {
        try {
            updateListData(blackCompanies);
            Map<String, Set<String>> data = new HashMap<>();
            data.put("blackCompanies", blackCompanies);
            data.put("blackRecruiters", blackRecruiters);
            data.put("blackJobs", blackJobs);
            String json = customJsonFormat(data);
            Files.write(Paths.get(path), json.getBytes());
        } catch (IOException e) {
            log.error("保存【{}】数据失败！", path);
        }
    }

    private void updateListData(Set<String> blackCompanies) {
        CHROME_DRIVER.get(Constants.CHAT_URL);
        SeleniumUtil.getWait(3);

        JavascriptExecutor js = CHROME_DRIVER;
        boolean shouldBreak = false;
        while (!shouldBreak) {
            try {
                WebElement bottom = CHROME_DRIVER.findElement(By.xpath(Elements.FINISHED_XPATH));
                if ("没有更多了".equals(bottom.getText())) {
                    shouldBreak = true;
                }
            } catch (Exception ignore) {
            }

            List<WebElement> items = CHROME_DRIVER.findElements(By.xpath(Elements.CHAT_LIST_ITEM_XPATH));
            for (int i = 0; i < items.size(); i++) {
                try {
                    WebElement companyElement = CHROME_DRIVER.findElements(By.xpath(Elements.COMPANY_NAME_IN_CHAT_XPATH)).get(i);
                    String companyName = companyElement.getText();
                    WebElement messageElement = CHROME_DRIVER.findElements(By.xpath(Elements.MESSAGE_IN_CHAT_XPATH)).get(i);
                    String message = messageElement.getText();
                    boolean match = message.contains("不") || message.contains("感谢") || message.contains("但") || message.contains("遗憾") || message.contains("需要本") || message.contains("对不");
                    boolean nomatch = message.contains("不是") || message.contains("不生");
                    if (match && !nomatch) {
                        log.info("黑名单公司：【{}】，信息：【{}】", companyName, message);
                        if (blackCompanies.stream().anyMatch(companyName::contains)) {
                            continue;
                        }
                        companyName = companyName.replaceAll("\\.{3}", "");
                        if (companyName.matches(".*(\\p{IsHan}{2,}|[a-zA-Z]{4,}).*")) {
                            blackCompanies.add(companyName);
                        }
                    }
                } catch (Exception e) {
                    log.error("寻找黑名单公司异常...");
                }
            }

            WebElement element;
            try {
                WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath(Elements.SCROLL_LOAD_XPATH)));
                element = CHROME_DRIVER.findElement(By.xpath(Elements.SCROLL_LOAD_XPATH));
            } catch (Exception e) {
                log.info("没找到滚动条...");
                break;
            }

            if (element != null) {
                try {
                    js.executeScript("arguments[0].scrollIntoView();", element);
                } catch (Exception e) {
                    log.error("滚动到元素出错", e);
                }
            } else {
                try {
                    js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                } catch (Exception e) {
                    log.error("滚动到页面底部出错", e);
                }
            }
        }
        log.info("黑名单公司数量：{}", blackCompanies.size());
    }

    private String customJsonFormat(Map<String, Set<String>> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        for (Map.Entry<String, Set<String>> entry : data.entrySet()) {
            sb.append("    \"").append(entry.getKey()).append("\": [\n");
            sb.append(entry.getValue().stream().map(s -> "        \"" + s + "\"").collect(Collectors.joining(",\n")));
            sb.append("\n    ],\n");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("\n}");
        return sb.toString();
    }
}