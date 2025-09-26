package getjobs.modules.dict.infrastructure.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import getjobs.common.enums.RecruitmentPlatformEnum;
import getjobs.modules.dict.api.DictBundle;
import getjobs.modules.dict.api.DictGroup;
import getjobs.modules.dict.api.DictGroupKey;
import getjobs.modules.dict.api.DictItem;
import getjobs.modules.dict.domain.DictProvider;
import getjobs.modules.dict.infrastructure.provider.dto.zhilian.ZhilianBaseData;
import getjobs.modules.dict.infrastructure.provider.dto.zhilian.ZhilianDictItem;
import getjobs.modules.dict.infrastructure.provider.dto.zhilian.ZhilianResponse;
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
public class ZhilianDictProviderImpl implements DictProvider {

    private final String baseDataUrl = "https://fe-api.zhaopin.com/c/i/search/base/data";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ZhilianDictProviderImpl(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public RecruitmentPlatformEnum platform() {
        return RecruitmentPlatformEnum.ZHILIAN_ZHAOPIN;
    }

    @Override
    public DictBundle fetchAll() {
        List<DictGroup> groups = new ArrayList<>();

        try {
            String response = webClient.get()
                    .uri(baseDataUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                ZhilianResponse<ZhilianBaseData> zhilianData = objectMapper.readValue(response,
                        objectMapper.getTypeFactory().constructParametricType(ZhilianResponse.class,
                                ZhilianBaseData.class));

                if (zhilianData.code() == 200 && zhilianData.data() != null) {
                    ZhilianBaseData data = zhilianData.data();

                    // 处理城市数据
                    processCityData(groups, data);

                    // 处理公司类型
                    if (data.companyType() != null) {
                        groups.add(new DictGroup(DictGroupKey.COMPANY_NATURE.key(),
                                data.companyType().stream()
                                        .filter(item -> item.deleted() == null || !item.deleted())
                                        .filter(item -> item.code() != null) // 过滤掉"不限"选项
                                        .map(item -> new DictItem(item.code(), item.name()))
                                        .collect(Collectors.toList())));
                    }

                    // 处理薪资类型
                    if (data.salaryType() != null) {
                        groups.add(new DictGroup(DictGroupKey.SALARY.key(),
                                data.salaryType().stream()
                                        .filter(item -> item.deleted() == null || !item.deleted())
                                        .filter(item -> item.code() != null) // 过滤掉"不限"选项
                                        .map(item -> parseSalaryItem(item))
                                        .collect(Collectors.toList())));
                    }

                    // 处理职位类别
                    if (data.position() != null) {
                        groups.add(new DictGroup(DictGroupKey.PART_TIME.key(),
                                flattenPositionItems(data.position())));
                    }

                    // 处理工作经验
                    if (data.workExpType() != null) {
                        groups.add(new DictGroup(DictGroupKey.EXPERIENCE.key(),
                                data.workExpType().stream()
                                        .filter(item -> item.deleted() == null || !item.deleted())
                                        .filter(item -> item.code() != null && !item.code().equals("-1")) // 过滤掉"不限"选项
                                        .map(item -> new DictItem(item.code(), item.name()))
                                        .collect(Collectors.toList())));
                    }

                    // 处理求职类型
                    if (data.jobStatus() != null) {
                        groups.add(new DictGroup(DictGroupKey.JOB_TYPE.key(),
                                data.jobStatus().stream()
                                        .filter(item -> item.deleted() == null || !item.deleted())
                                        .filter(item -> item.code() != null && !item.code().equals("-1")) // 过滤掉"不限"选项
                                        .map(item -> new DictItem(item.code(), item.name()))
                                        .collect(Collectors.toList())));
                    }

                    // 处理学历要求
                    if (data.educationType() != null) {
                        groups.add(new DictGroup(DictGroupKey.DEGREE.key(),
                                data.educationType().stream()
                                        .filter(item -> item.deleted() == null || !item.deleted())
                                        .filter(item -> item.code() != null && !item.code().equals("-1")) // 过滤掉"不限"选项
                                        .map(item -> new DictItem(item.code(), item.name()))
                                        .collect(Collectors.toList())));
                    }

                    // 处理公司规模
                    if (data.companySize() != null) {
                        groups.add(new DictGroup(DictGroupKey.SCALE.key(),
                                data.companySize().stream()
                                        .filter(item -> item.deleted() == null || !item.deleted())
                                        .filter(item -> item.code() != null && !item.code().equals("-1")) // 过滤掉"不限"选项
                                        .filter(item -> !item.name().trim().isEmpty()) // 过滤掉名称为空的选项（如"保密"选项）
                                        .map(item -> new DictItem(item.code(), item.name()))
                                        .collect(Collectors.toList())));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取智联招聘字典数据失败: {}", e.getMessage());
        }

        return new DictBundle(RecruitmentPlatformEnum.ZHILIAN_ZHAOPIN, groups);
    }

    /**
     * 处理城市数据，包含地铁和城市列表
     */
    private void processCityData(List<DictGroup> groups, ZhilianBaseData data) {
        Set<DictItem> cityItemsSet = new HashSet<>();

        // 从地铁数据中提取城市信息
        if (data.subway() != null) {
            cityItemsSet.addAll(data.subway().stream()
                    .filter(subway -> subway.deleted() == null || !subway.deleted())
                    .filter(subway -> subway.code() != null)
                    .map(subway -> new DictItem(subway.code(), subway.name()))
                    .toList());
        }

        // 从城市列表中提取城市信息
        if (data.cityList() != null) {
            cityItemsSet.addAll(data.cityList().stream()
                    .filter(city -> city.deleted() == null || !city.deleted())
                    .filter(city -> city.code() != null)
                    .map(city -> new DictItem(city.code(), city.name()))
                    .toList());
        }

        if (!cityItemsSet.isEmpty()) {
            groups.add(new DictGroup(DictGroupKey.CITY.key(), new ArrayList<>(cityItemsSet)));
        }
    }

    /**
     * 解析薪资项，提取薪资范围
     */
    private DictItem parseSalaryItem(ZhilianDictItem item) {
        String code = item.code();
        String name = item.name();

        // 解析薪资范围，格式如 "4001,6000" 或 "0000,9999999"
        Integer lowSalary = null;
        Integer highSalary = null;

        if (code != null && code.contains(",")) {
            try {
                String[] parts = code.split(",");
                if (parts.length == 2) {
                    int low = Integer.parseInt(parts[0]);
                    int high = Integer.parseInt(parts[1]);

                    // 处理特殊情况：不限薪资
                    if (low == 0 && high == 9999999) {
                        // 不限薪资不设置范围
                    } else if (low == 0) {
                        // 如"4K以下"，只设置高薪资
                        highSalary = high;
                    } else if (high == 9999999) {
                        // 如"50K以上"，只设置低薪资
                        lowSalary = low;
                    } else {
                        // 正常范围
                        lowSalary = low;
                        highSalary = high;
                    }
                }
            } catch (NumberFormatException e) {
                log.debug("解析薪资范围失败: {}", code);
            }
        }

        return new DictItem(code, name, lowSalary, highSalary);
    }

    /**
     * 扁平化职位分类数据，包含子分类
     */
    private List<DictItem> flattenPositionItems(List<ZhilianDictItem> positions) {
        List<DictItem> result = new ArrayList<>();

        for (ZhilianDictItem position : positions) {
            if (position.deleted() == null || !position.deleted()) {
                // 添加主分类
                if (position.code() != null) {
                    result.add(new DictItem(position.code(), position.name()));
                }

                // 递归添加子分类
                if (position.sublist() != null && !position.sublist().isEmpty()) {
                    result.addAll(flattenPositionItems(position.sublist()));
                }
            }
        }

        return result;
    }
}
