package getjobs.modules.dict.infrastructure.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import getjobs.enums.RecruitmentPlatformEnum;
import getjobs.modules.dict.api.DictBundle;
import getjobs.modules.dict.api.DictGroup;
import getjobs.modules.dict.api.DictGroupKey;
import getjobs.modules.dict.api.DictItem;
import getjobs.modules.dict.domain.DictProvider;
import getjobs.modules.dict.infrastructure.provider.dto.job51.Job51CityGroup;
import getjobs.modules.dict.infrastructure.provider.dto.job51.Job51Response;
import getjobs.modules.dict.infrastructure.provider.dto.job51.DictJsonResponse;
import getjobs.modules.dict.infrastructure.provider.dto.ConditionsData;
import getjobs.modules.dict.infrastructure.provider.dto.ConditionItem;
import getjobs.modules.dict.infrastructure.provider.dto.SalaryItem;
import getjobs.modules.job51.service.Job51ElementLocators;
import getjobs.config.Job51DictConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class Job51DictProviderImpl implements DictProvider {

    private final String cityJsonUrl = "https://js.51jobcdn.com/in/js/2023/dd/dd_city.json";

    // Job51搜索页面URL，用于获取筛选条件数据
    private final String searchPageUrl = "https://we.51job.com/pc/search";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Job51DictConfig job51DictConfig;

    public Job51DictProviderImpl(WebClient webClient, ObjectMapper objectMapper, Job51DictConfig job51DictConfig) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.job51DictConfig = job51DictConfig;
    }

    @Override
    public RecruitmentPlatformEnum platform() {
        return RecruitmentPlatformEnum.JOB_51;
    }

    @Override
    public DictBundle fetchAll() {
        List<DictGroup> groups = new ArrayList<>();

        try {
            // 获取城市数据
            String cityResponse = webClient.get()
                    .uri(cityJsonUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (cityResponse != null) {
                Job51Response cityData = objectMapper.readValue(cityResponse, Job51Response.class);

                if (cityData.items() != null) {
                    // 处理城市数据 - 使用Set去重，避免热门城市和其他分组中的城市重复
                    Set<DictItem> cityItemsSet = new HashSet<>();

                    // 遍历所有城市分组
                    for (Job51CityGroup group : cityData.items()) {
                        if (group.items() != null&&"0".equals(group.type())) {
                            // 添加城市项目
                            cityItemsSet.addAll(group.items().stream()
                                    .map(city -> new DictItem(city.code(), city.value()))
                                    .toList());
                        }
                    }

                    groups.add(new DictGroup(DictGroupKey.CITY.key(), new ArrayList<>(cityItemsSet)));
                }
            }

            DictBundle dictBundle = fetchFromConfig();
            groups.addAll(dictBundle.groups());
        } catch (Exception e) {
            // 记录错误日志，但不抛出异常，返回空的数据包
            log.warn("获取51job招聘字典数据失败: {}", e.getMessage());
        }

        return new DictBundle(RecruitmentPlatformEnum.JOB_51, groups);
    }


    /**
     * 通过playwright获取页面筛选条件数据的方法
     * 访问 https://we.51job.com/pc/search 页面，解析筛选条件数据
     *
     * @param page Playwright页面对象
     * @return 条件数据，如果获取失败则返回null
     */
    public ConditionsData fetchConditionsFromPage(Page page) {
        try {
            // 访问Job51搜索页面
            page.navigate(searchPageUrl);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // 等待筛选条件加载完成
            page.waitForSelector("div.j_filter");

            // 使用Job51ElementLocators获取页面筛选条件
            Map<String, List<Job51ElementLocators.FilterOption>> filterOptions = Job51ElementLocators
                    .getFilterOptions(page);

            // 解析筛选条件并构建条件数据
            ConditionsData conditionsData = parseFilterOptionsToConditionsData(filterOptions);

            log.info("成功通过playwright获取Job51页面筛选条件数据");
            return conditionsData;

        } catch (Exception e) {
            log.error("通过playwright获取Job51页面筛选条件数据失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将页面筛选条件解析为ConditionsData对象
     *
     * @param filterOptions 页面筛选条件映射
     * @return ConditionsData对象
     */
    private ConditionsData parseFilterOptionsToConditionsData(
            Map<String, List<Job51ElementLocators.FilterOption>> filterOptions) {
        List<ConditionItem> payTypeList = new ArrayList<>();
        List<ConditionItem> experienceList = new ArrayList<>();
        List<SalaryItem> salaryList = new ArrayList<>();
        List<ConditionItem> stageList = new ArrayList<>();
        List<ConditionItem> companyNatureList = new ArrayList<>();
        List<ConditionItem> scaleList = new ArrayList<>();
        List<ConditionItem> partTimeList = new ArrayList<>();
        List<ConditionItem> degreeList = new ArrayList<>();
        List<ConditionItem> jobTypeList = new ArrayList<>();

        for (Map.Entry<String, List<Job51ElementLocators.FilterOption>> entry : filterOptions.entrySet()) {
            String label = entry.getKey();
            List<Job51ElementLocators.FilterOption> options = entry.getValue();

            // 根据label分类处理不同的条件
            switch (label) {
                case "月薪范围：" -> {
                    for (Job51ElementLocators.FilterOption option : options) {
                        String text = option.getText();
                        // 解析薪资范围，提取最低和最高薪资
                        Integer lowSalary = parseSalaryLow(text);
                        Integer highSalary = parseSalaryHigh(text);
                        salaryList.add(new SalaryItem(text, text, lowSalary, highSalary));
                    }
                }
                case "工作年限：" -> {
                    for (Job51ElementLocators.FilterOption option : options) {
                        String text = option.getText();
                        experienceList.add(new ConditionItem(text, text));
                    }
                }
                case "学历要求：" -> {
                    for (Job51ElementLocators.FilterOption option : options) {
                        String text = option.getText();
                        degreeList.add(new ConditionItem(text, text));
                    }
                }
                case "公司性质：" -> {
                    for (Job51ElementLocators.FilterOption option : options) {
                        String text = option.getText();
                        companyNatureList.add(new ConditionItem(text, text));
                    }
                }
                case "公司规模：" -> {
                    for (Job51ElementLocators.FilterOption option : options) {
                        String text = option.getText();
                        scaleList.add(new ConditionItem(text, text));
                    }
                }
                case "工作类型：" -> {
                    for (Job51ElementLocators.FilterOption option : options) {
                        String text = option.getText();
                        jobTypeList.add(new ConditionItem(text, text));
                    }
                }
                default -> {
                    // 其他未分类的条件，可以根据需要添加
                    log.debug("未处理的筛选条件: {}", label);
                }
            }
        }

        return new ConditionsData(
                payTypeList.isEmpty() ? null : payTypeList,
                experienceList.isEmpty() ? null : experienceList,
                salaryList.isEmpty() ? null : salaryList,
                stageList.isEmpty() ? null : stageList,
                companyNatureList.isEmpty() ? null : companyNatureList,
                scaleList.isEmpty() ? null : scaleList,
                partTimeList.isEmpty() ? null : partTimeList,
                degreeList.isEmpty() ? null : degreeList,
                jobTypeList.isEmpty() ? null : jobTypeList);
    }

    /**
     * 解析薪资文本，提取最低薪资
     *
     * @param salaryText 薪资文本，如"3-5千"、"5万以上"
     * @return 最低薪资（单位：元），如果无法解析则返回null
     */
    private Integer parseSalaryLow(String salaryText) {
        try {
            if (salaryText.contains("以下") || salaryText.contains("以下")) {
                // 如"3千以下"
                String number = salaryText.replaceAll("[^0-9]", "");
                return Integer.parseInt(number) * 1000;
            } else if (salaryText.contains("-")) {
                // 如"3-5千"
                String[] parts = salaryText.split("-");
                String lowPart = parts[0].replaceAll("[^0-9]", "");
                String unit = parts[1].replaceAll("[0-9]", "");
                int multiplier = getSalaryMultiplier(unit);
                return Integer.parseInt(lowPart) * multiplier;
            } else if (salaryText.contains("以上")) {
                // 如"5万以上"
                String number = salaryText.replaceAll("[^0-9]", "");
                String unit = salaryText.replaceAll("[0-9]", "");
                int multiplier = getSalaryMultiplier(unit);
                return Integer.parseInt(number) * multiplier;
            }
        } catch (Exception e) {
            log.debug("解析薪资失败: {}", salaryText);
        }
        return null;
    }

    /**
     * 解析薪资文本，提取最高薪资
     *
     * @param salaryText 薪资文本，如"3-5千"、"5万以上"
     * @return 最高薪资（单位：元），如果无法解析则返回null
     */
    private Integer parseSalaryHigh(String salaryText) {
        try {
            if (salaryText.contains("以下")) {
                // 如"3千以下"，最高薪资为0
                return 0;
            } else if (salaryText.contains("-")) {
                // 如"3-5千"
                String[] parts = salaryText.split("-");
                String highPart = parts[1].replaceAll("[^0-9]", "");
                String unit = parts[1].replaceAll("[0-9]", "");
                int multiplier = getSalaryMultiplier(unit);
                return Integer.parseInt(highPart) * multiplier;
            } else if (salaryText.contains("以上")) {
                // 如"5万以上"，最高薪资为null（无上限）
                return null;
            }
        } catch (Exception e) {
            log.debug("解析薪资失败: {}", salaryText);
        }
        return null;
    }

    /**
     * 根据单位获取薪资倍数
     *
     * @param unit 单位文本，如"千"、"万"
     * @return 倍数
     */
    private int getSalaryMultiplier(String unit) {
        if (unit.contains("万")) {
            return 10000;
        } else if (unit.contains("千")) {
            return 1000;
        }
        return 1;
    }

    /**
     * 直接从application.yml配置中读取dict-json并解析字典数据
     *
     * @return 字典数据包
     */
    public DictBundle fetchFromConfig() {
        List<DictGroup> groups = new ArrayList<>();

        try {
            // 从配置中读取dict-json字符串
            String dictJsonStr = job51DictConfig.getDictJson();
            if (dictJsonStr == null || dictJsonStr.trim().isEmpty()) {
                log.warn("dict-json配置为空");
                return new DictBundle(platform(), groups);
            }

            // 解析JSON
            DictJsonResponse response = objectMapper.readValue(dictJsonStr, DictJsonResponse.class);

            if (response == null || response.getResultBody() == null) {
                log.warn("解析dict-json失败：响应数据为空");
                return new DictBundle(platform(), groups);
            }

            DictJsonResponse.ResultBody resultBody = response.getResultBody();

            // 处理行业字典
            if (resultBody.getIndustry() != null) {
                groups.add(new DictGroup("industryList",
                        resultBody.getIndustry().stream()
                                .map(item -> new DictItem(item.getId(), item.getValue()))
                                .toList()));
            }

            // 处理公司类型字典 - 使用COMPANY_NATURE作为公司类型的key
            if (resultBody.getCompanyType() != null) {
                groups.add(new DictGroup(DictGroupKey.COMPANY_NATURE.key(),
                        resultBody.getCompanyType().stream()
                                .map(item -> new DictItem(item.getId(), item.getValue()))
                                .toList()));
            }

            // 处理工作经验字典
            if (resultBody.getWorkYear() != null) {
                groups.add(new DictGroup(DictGroupKey.EXPERIENCE.key(),
                        resultBody.getWorkYear().stream()
                                .map(item -> new DictItem(item.getId(), item.getValue()))
                                .toList()));
            }

            // 处理薪资字典
            if (resultBody.getSalary() != null) {
                groups.add(new DictGroup(DictGroupKey.SALARY.key(),
                        resultBody.getSalary().stream()
                                .map(item -> new DictItem(item.getId(), item.getValue()))
                                .toList()));
            }

            // 处理公司规模字典
            if (resultBody.getCompanySize() != null) {
                groups.add(new DictGroup(DictGroupKey.SCALE.key(),
                        resultBody.getCompanySize().stream()
                                .map(item -> new DictItem(item.getId(), item.getValue()))
                                .toList()));
            }

            // 处理学历字典
            if (resultBody.getDegree() != null) {
                groups.add(new DictGroup(DictGroupKey.DEGREE.key(),
                        resultBody.getDegree().stream()
                                .map(item -> new DictItem(item.getId(), item.getValue()))
                                .toList()));
            }

            // 处理工作性质字典
            if (resultBody.getJobTerm() != null) {
                groups.add(new DictGroup(DictGroupKey.JOB_TYPE.key(),
                        resultBody.getJobTerm().stream()
                                .map(item -> new DictItem(item.getId(), item.getValue()))
                                .toList()));
            }

            log.info("成功从配置中解析出{}个字典组", groups.size());

        } catch (Exception e) {
            log.error("从配置解析字典数据失败", e);
        }

        return new DictBundle(platform(), groups);
    }
}
