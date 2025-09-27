package getjobs.modules.dict.infrastructure.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import getjobs.common.enums.RecruitmentPlatformEnum;
import getjobs.config.LiepinDictConfig;
import getjobs.modules.dict.api.DictBundle;
import getjobs.modules.dict.api.DictGroup;
import getjobs.modules.dict.api.DictGroupKey;
import getjobs.modules.dict.api.DictItem;
import getjobs.modules.dict.domain.DictProvider;
import getjobs.modules.dict.infrastructure.provider.dto.liepin.LiepinDictResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LiepinDictProviderImpl implements DictProvider {

    private final ObjectMapper objectMapper;
    private final LiepinDictConfig liepinDictConfig;

    public LiepinDictProviderImpl(ObjectMapper objectMapper, LiepinDictConfig liepinDictConfig) {
        this.objectMapper = objectMapper;
        this.liepinDictConfig = liepinDictConfig;
    }

    @Override
    public RecruitmentPlatformEnum platform() {
        return RecruitmentPlatformEnum.LIEPIN;
    }

    @Override
    public DictBundle fetchAll() {
        List<DictGroup> groups = new ArrayList<>();
        try {
            DictBundle dictBundle = fetchFromConfig();
            groups.addAll(dictBundle.groups());
        } catch (Exception e) {
            log.warn("获取猎聘招聘字典数据失败: {}", e.getMessage());
        }
        return new DictBundle(platform(), groups);
    }

    public DictBundle fetchFromConfig() {
        List<DictGroup> groups = new ArrayList<>();

        try {
            String dictJsonStr = liepinDictConfig.getDictJson();
            if (dictJsonStr == null || dictJsonStr.trim().isEmpty()) {
                log.warn("liepin.dict-json配置为空");
                return new DictBundle(platform(), groups);
            }

            LiepinDictResponse response = objectMapper.readValue(dictJsonStr, LiepinDictResponse.class);

            if (response == null || response.getData() == null) {
                log.warn("解析liepin.dict-json失败：响应数据为空");
                return new DictBundle(platform(), groups);
            }

            LiepinDictResponse.LiepinDictData data = response.getData();

            if (data.getWorkExperiences() != null) {
                groups.add(new DictGroup(DictGroupKey.EXPERIENCE.key(),
                        data.getWorkExperiences().stream()
                                .map(item -> new DictItem(item.getCode(), item.getName()))
                                .collect(Collectors.toList())));
            }

            if (data.getSalaries() != null) {
                groups.add(new DictGroup(DictGroupKey.SALARY.key(),
                        data.getSalaries().stream()
                                .map(item -> new DictItem(item.getCode(), item.getName()))
                                .collect(Collectors.toList())));
            }

            if (data.getCompScales() != null) {
                groups.add(new DictGroup(DictGroupKey.SCALE.key(),
                        data.getCompScales().stream()
                                .map(item -> new DictItem(item.getCode(), item.getName()))
                                .collect(Collectors.toList())));
            }

            if (data.getEducations() != null) {
                groups.add(new DictGroup(DictGroupKey.DEGREE.key(),
                        data.getEducations().stream()
                                .map(item -> new DictItem(item.getCode(), item.getName()))
                                .collect(Collectors.toList())));
            }
            if (data.getCompNatures() != null) {
                groups.add(new DictGroup(DictGroupKey.COMPANY_NATURE.key(),
                        data.getCompNatures().stream()
                                .map(item -> new DictItem(item.getCode(), item.getName()))
                                .collect(Collectors.toList())));
            }

            if (data.getIndustries() != null) {
                groups.add(new DictGroup(DictGroupKey.INDUSTRY.key(),
                        data.getIndustries().stream()
                                .map(item -> new DictItem(item.getCode(), item.getName()))
                                .collect(Collectors.toList())));
            }


            // 处理工作性质字典
            if (data.getJobKinds() != null) {
                groups.add(new DictGroup(DictGroupKey.JOB_TYPE.key(),
                        data.getJobKinds().stream()
                                .map(item -> new DictItem(item.getCode(), item.getName()))
                                .toList()));
            }

            // 融资阶段
            if (data.getFinanceStages() != null) {
                groups.add(new DictGroup(DictGroupKey.STAGE.key(),
                        data.getFinanceStages().stream()
                                .map(item -> new DictItem(String.valueOf(item.getCode()), item.getName()))
                                .collect(Collectors.toList())));
            }

            // 招聘者活跃度
            if (data.getPubTimes() != null) {
                groups.add(new DictGroup(DictGroupKey.PUBTIMES.key(),
                        data.getPubTimes().stream()
                                .map(item -> new DictItem(String.valueOf(item.getCode()), item.getName()))
                                .collect(Collectors.toList())));
            }


            log.info("成功从配置中解析出{}个猎聘字典组", groups.size());

        } catch (Exception e) {
            log.error("从配置解析猎聘字典数据失败", e);
        }

        return new DictBundle(platform(), groups);
    }
}
