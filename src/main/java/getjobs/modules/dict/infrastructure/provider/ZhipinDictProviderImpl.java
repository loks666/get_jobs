package getjobs.modules.dict.infrastructure.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import getjobs.common.enums.RecruitmentPlatformEnum;
import getjobs.modules.dict.api.DictBundle;
import getjobs.modules.dict.api.DictGroup;
import getjobs.modules.dict.api.DictGroupKey;
import getjobs.modules.dict.api.DictItem;
import getjobs.modules.dict.domain.DictProvider;
import getjobs.modules.dict.infrastructure.provider.dto.boss.CityGroupData;
import getjobs.modules.dict.infrastructure.provider.dto.ConditionsData;
import getjobs.modules.dict.infrastructure.provider.dto.boss.ZhipinResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ZhipinDictProviderImpl implements DictProvider {

    private final String conditionsJsonUrl = "https://www.zhipin.com/wapi/zpgeek/pc/all/filter/conditions.json";

    private final String cityGroupJsonUrl = "https://www.zhipin.com/wapi/zpCommon/data/cityGroup.json";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ZhipinDictProviderImpl(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public RecruitmentPlatformEnum platform() {
        return RecruitmentPlatformEnum.BOSS_ZHIPIN;
    }

    @Override
    public DictBundle fetchAll() {
        List<DictGroup> groups = new ArrayList<>();

        try {
            // 获取城市数据
            String cityResponse = webClient.get()
                    .uri(cityGroupJsonUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (cityResponse != null) {
                ZhipinResponse<CityGroupData> cityData = objectMapper.readValue(cityResponse,
                        objectMapper.getTypeFactory().constructParametricType(ZhipinResponse.class,
                                CityGroupData.class));

                if (cityData.code() == 0 && cityData.zpData() != null) {
                    // 处理城市数据 - 使用Set去重，避免热门城市和城市分组中的城市重复
                    Set<DictItem> cityItemsSet = new HashSet<>();

                    // 添加热门城市
                    if (cityData.zpData().hotCityList() != null) {
                        cityItemsSet.addAll(cityData.zpData().hotCityList().stream()
                                .map(city -> new DictItem(String.valueOf(city.code()), city.name()))
                                .toList());
                    }

                    // 添加所有城市分组中的城市
                    if (cityData.zpData().cityGroup() != null) {
                        cityItemsSet.addAll(cityData.zpData().cityGroup().stream()
                                .flatMap(group -> group.cityList().stream())
                                .map(city -> new DictItem(String.valueOf(city.code()), city.name()))
                                .toList());
                    }

                    groups.add(new DictGroup(DictGroupKey.CITY.key(), new ArrayList<>(cityItemsSet)));
                }
            }

            // 获取条件数据
            String conditionsResponse = webClient.get()
                    .uri(conditionsJsonUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (conditionsResponse != null) {
                ZhipinResponse<ConditionsData> conditionsData = objectMapper.readValue(conditionsResponse,
                        objectMapper.getTypeFactory().constructParametricType(ZhipinResponse.class,
                                ConditionsData.class));

                if (conditionsData.code() == 0 && conditionsData.zpData() != null) {
                    ConditionsData data = conditionsData.zpData();

                    // 处理各种条件列表
                    if (data.payTypeList() != null) {
                        groups.add(new DictGroup(DictGroupKey.PAY_TYPE.key(),
                                data.payTypeList().stream()
                                        .map(item -> new DictItem(String.valueOf(item.code()), item.name()))
                                        .collect(Collectors.toList())));
                    }

                    if (data.experienceList() != null) {
                        groups.add(new DictGroup(DictGroupKey.EXPERIENCE.key(),
                                data.experienceList().stream()
                                        .map(item -> new DictItem(String.valueOf(item.code()), item.name()))
                                        .collect(Collectors.toList())));
                    }

                    if (data.salaryList() != null) {
                        groups.add(new DictGroup(DictGroupKey.SALARY.key(),
                                data.salaryList().stream()
                                        .map(item -> new DictItem(String.valueOf(item.code()), item.name(),
                                                item.lowSalary() == 0 ? null : item.lowSalary(),
                                                item.highSalary() == 0 ? null : item.highSalary()))
                                        .collect(Collectors.toList())));
                    }

                    if (data.stageList() != null) {
                        groups.add(new DictGroup(DictGroupKey.STAGE.key(),
                                data.stageList().stream()
                                        .map(item -> new DictItem(String.valueOf(item.code()), item.name()))
                                        .collect(Collectors.toList())));
                    }

                    if (data.companyNatureList() != null) {
                        groups.add(new DictGroup(DictGroupKey.COMPANY_NATURE.key(),
                                data.companyNatureList().stream()
                                        .map(item -> new DictItem(String.valueOf(item.code()), item.name()))
                                        .collect(Collectors.toList())));
                    }

                    if (data.scaleList() != null) {
                        groups.add(new DictGroup(DictGroupKey.SCALE.key(),
                                data.scaleList().stream()
                                        .map(item -> new DictItem(String.valueOf(item.code()), item.name()))
                                        .collect(Collectors.toList())));
                    }

                    if (data.partTimeList() != null) {
                        groups.add(new DictGroup(DictGroupKey.PART_TIME.key(),
                                data.partTimeList().stream()
                                        .map(item -> new DictItem(String.valueOf(item.code()), item.name()))
                                        .collect(Collectors.toList())));
                    }

                    if (data.degreeList() != null) {
                        groups.add(new DictGroup(DictGroupKey.DEGREE.key(),
                                data.degreeList().stream()
                                        .map(item -> new DictItem(String.valueOf(item.code()), item.name()))
                                        .collect(Collectors.toList())));
                    }

                    if (data.jobTypeList() != null) {
                        groups.add(new DictGroup(DictGroupKey.JOB_TYPE.key(),
                                data.jobTypeList().stream()
                                        .map(item -> new DictItem(String.valueOf(item.code()), item.name()))
                                        .collect(Collectors.toList())));
                    }
                }
            }
        } catch (Exception e) {
            // 记录错误日志，但不抛出异常，返回空的数据包
            // 在实际项目中应该使用日志框架记录错误
            log.warn("获取BOSS直聘招聘字典数据失败: {}", e.getMessage());
        }

        return new DictBundle(RecruitmentPlatformEnum.BOSS_ZHIPIN, groups);
    }
}