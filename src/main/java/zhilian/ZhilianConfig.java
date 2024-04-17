package zhilian;

import lombok.Data;
import lombok.SneakyThrows;
import utils.JobUtils;

import java.util.List;

@Data
public class ZhilianConfig {
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
    public static ZhilianConfig init() {
        ZhilianConfig config = JobUtils.getConfig(ZhilianConfig.class);
        // 转换城市编码
        config.setCityCode(ZhilianEnum.CityCode.forValue(config.getCityCode()).getCode());
        return config;
    }

}
