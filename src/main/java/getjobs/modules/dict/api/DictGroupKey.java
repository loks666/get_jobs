package getjobs.modules.dict.api;

import java.util.Arrays;

/**
 * 所有平台统一的字典分组Key
 */
public enum DictGroupKey {

    CITY("cityList", "城市"),
    PAY_TYPE("payTypeList", "结算方式"),
    EXPERIENCE("experienceList", "工作经验"),
    SALARY("salaryList", "薪资区间"),
    STAGE("stageList", "融资阶段"),
    SCALE("scaleList", "公司规模"),
    PART_TIME("partTimeList", "兼职类型"),
    DEGREE("degreeList", "学历"),
    JOB_TYPE("jobTypeList", "工作类型");

    private final String key;
    private final String description;

    DictGroupKey(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String description() {
        return description;
    }

    public static DictGroupKey fromKey(String key) {
        return Arrays.stream(values())
                .filter(e -> e.key.equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无效的字典key: " + key));
    }
}