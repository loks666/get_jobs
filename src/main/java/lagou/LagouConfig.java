package lagou;

import lombok.Data;
import lombok.SneakyThrows;
import utils.JobUtils;

import java.util.List;

@Data
public class LagouConfig {
    /**
     * 搜索关键词列表
     */
    private List<String> keywords;

    /**
     * 城市编码
     */
    private String cityCode;

    /**
     * 薪资范围
     */
    private String salary;

   

    @SneakyThrows
    public static LagouConfig init() {
        LagouConfig config = JobUtils.getConfig(LagouConfig.class);
        // 转换城市编码
        config.setCityCode(LagouEnum.CityCode.forValue(config.getCityCode()).getCode());
        return config;
    }

}
