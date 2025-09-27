package getjobs.modules.dict.infrastructure.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import getjobs.common.enums.RecruitmentPlatformEnum;
import getjobs.config.Job51DictConfig;
import getjobs.modules.dict.api.DictBundle;
import getjobs.modules.dict.api.DictGroup;
import getjobs.modules.dict.api.DictGroupKey;
import getjobs.modules.dict.api.DictItem;
import getjobs.modules.dict.domain.DictProvider;
import getjobs.modules.dict.infrastructure.provider.dto.job51.DictJsonResponse;
import getjobs.modules.dict.infrastructure.provider.dto.job51.Job51CityGroup;
import getjobs.modules.dict.infrastructure.provider.dto.job51.Job51Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class Job51DictProviderImpl implements DictProvider {

    private final String cityJsonUrl = "https://js.51jobcdn.com/in/js/2023/dd/dd_city.json";

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
