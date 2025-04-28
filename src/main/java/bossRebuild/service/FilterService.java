package bossRebuild.service;

import ai.AiConfig;
import ai.AiFilter;
import ai.AiService;
import bossRebuild.config.BossConfig;
import bossRebuild.constants.Constants;
import bossRebuild.constants.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static utils.Constant.CHROME_DRIVER;

public class FilterService {
    private static final Logger log = LoggerFactory.getLogger(FilterService.class);
    private final BossConfig config;
    private final Set<String> blackCompanies;
    private final Set<String> blackRecruiters;
    private final Set<String> blackJobs;

    public FilterService(BossConfig config, Set<String> blackCompanies, Set<String> blackRecruiters, Set<String> blackJobs) {
        this.config = config;
        this.blackCompanies = blackCompanies;
        this.blackRecruiters = blackRecruiters;
        this.blackJobs = blackJobs;
    }

    public boolean filterJob(String companyName, String recruiterName, String jobName) {
        return blackCompanies.stream().anyMatch(companyName::contains) ||
                blackRecruiters.stream().anyMatch(recruiterName::contains) ||
                blackJobs.stream().anyMatch(jobName::contains);
    }

    public boolean isSalaryNotExpected(String salary) {
        try {
            List<Integer> expectedSalary = config.getExpectedSalary();
            if (!hasExpectedSalary(expectedSalary)) {
                return false;
            }

            salary = removeYearBonusText(salary);
            if (!isSalaryInExpectedFormat(salary)) {
                return true;
            }

            salary = cleanSalaryText(salary);
            String jobType = detectJobType(salary);
            salary = removeDayUnitIfNeeded(salary);

            Integer[] jobSalaryRange = parseSalaryRange(salary);
            return isSalaryOutOfRange(jobSalaryRange,
                    getMinimumSalary(expectedSalary),
                    getMaximumSalary(expectedSalary),
                    jobType);
        } catch (Exception e) {
            log.error("岗位薪资获取异常！{}", e.getMessage(), e);
            return true;
        }
    }

    public boolean isDeadHR() {
        if (!config.getFilterDeadHR()) {
            return false;
        }
        try {
            String activeTimeText = CHROME_DRIVER.findElement(org.openqa.selenium.By.xpath(Elements.ACTIVE_TIME_XPATH)).getText();
            log.info("{}：{}", getCompanyAndHR(), activeTimeText);
            return containsDeadStatus(activeTimeText, Constants.DEAD_STATUS);
        } catch (Exception e) {
            log.info("没有找到【{}】的活跃状态, 默认此岗位将会投递...", getCompanyAndHR());
            return false;
        }
    }

    public boolean isTargetJob(String keyword, String jobName) {
        boolean keywordIsAI = false;
        for (String target : new String[]{"大模型", "AI"}) {
            if (keyword.contains(target)) {
                keywordIsAI = true;
                break;
            }
        }

        boolean jobIsDesign = false;
        for (String designOrVision : new String[]{"设计", "视觉", "产品", "运营"}) {
            if (jobName.contains(designOrVision)) {
                jobIsDesign = true;
                break;
            }
        }

        boolean jobIsAI = false;
        for (String target : new String[]{"AI", "人工智能", "大模型", "生成"}) {
            if (jobName.contains(target)) {
                jobIsAI = true;
                break;
            }
        }

        if (keywordIsAI) {
            if (jobIsDesign) {
                return false;
            } else if (!jobIsAI) {
                return true;
            }
        }
        return true;
    }

    public AiFilter checkJob(String keyword, String jobName, String jd) {
        AiConfig aiConfig = AiConfig.init();
        String requestMessage = String.format(aiConfig.getPrompt(), aiConfig.getIntroduce(), keyword, jobName, jd, config.getSayHi());
        String result = AiService.sendRequest(requestMessage);
        return result.contains("false") ? new AiFilter(false) : new AiFilter(true, result);
    }

    private boolean hasExpectedSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && !expectedSalary.isEmpty();
    }

    private String removeYearBonusText(String salary) {
        if (salary.contains("薪")) {
            return salary.replaceAll("·\\d+薪", "");
        }
        return salary;
    }

    private boolean isSalaryInExpectedFormat(String salaryText) {
        return salaryText.contains("K") || salaryText.contains("k");
    }

    private String cleanSalaryText(String salaryText) {
        salaryText = salaryText.replace("K", "").replace("k", "");
        int dotIndex = salaryText.indexOf('·');
        if (dotIndex != -1) {
            salaryText = salaryText.substring(0, dotIndex);
        }
        return salaryText;
    }

    private String detectJobType(String salary) {
        if (salary.contains("元/天")) {
            return "day";
        }
        return "month";
    }

    private String removeDayUnitIfNeeded(String salary) {
        if (salary.contains("元/天")) {
            return salary.replaceAll("元/天", "");
        }
        return salary;
    }

    private Integer getMinimumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && !expectedSalary.isEmpty() ? expectedSalary.get(0) : null;
    }

    private Integer getMaximumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && expectedSalary.size() > 1 ? expectedSalary.get(1) : null;
    }

    private boolean isSalaryOutOfRange(Integer[] jobSalary, Integer miniSalary, Integer maxSalary, String jobType) {
        if (jobSalary == null) {
            return true;
        }
        if (miniSalary == null) {
            return false;
        }
        if (Objects.equals("day", jobType)) {
            maxSalary = BigDecimal.valueOf(maxSalary).multiply(BigDecimal.valueOf(1000)).divide(BigDecimal.valueOf(21.75), 0, RoundingMode.HALF_UP).intValue();
            miniSalary = BigDecimal.valueOf(miniSalary).multiply(BigDecimal.valueOf(1000)).divide(BigDecimal.valueOf(21.75), 0, RoundingMode.HALF_UP).intValue();
        }
        if (jobSalary[1] < miniSalary) {
            return true;
        }
        return maxSalary != null && jobSalary[0] > maxSalary;
    }

    private Integer[] parseSalaryRange(String salaryText) {
        try {
            return Arrays.stream(salaryText.split("-"))
                    .map(s -> s.replaceAll("[^0-9]", ""))
                    .map(Integer::parseInt)
                    .toArray(Integer[]::new);
        } catch (Exception e) {
            log.error("薪资解析异常！{}", e.getMessage(), e);
            return null;
        }
    }

    public static boolean containsDeadStatus(String activeTimeText, List<String> deadStatus) {
        for (String status : deadStatus) {
            if (activeTimeText.contains(status)) {
                return true;
            }
        }
        return false;
    }

    String getCompanyAndHR() {
        return CHROME_DRIVER.findElement(org.openqa.selenium.By.xpath(Elements.COMPANY_AND_HR_XPATH)).getText().replaceAll("\n", "");
    }
}