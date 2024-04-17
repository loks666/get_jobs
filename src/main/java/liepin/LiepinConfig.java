package liepin;

import lombok.Data;
import lombok.SneakyThrows;
import utils.JobUtils;

import java.util.List;

@Data
public class LiepinConfig {
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
    public static LiepinConfig init() {
        LiepinConfig config = JobUtils.getConfig(LiepinConfig.class);
        // 转换城市编码
        config.setCityCode(LiepinEnum.CityCode.forValue(config.getCityCode()).getCode());
        return config;
    }

}
