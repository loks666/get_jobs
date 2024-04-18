package lagou;

import lombok.Data;
import lombok.SneakyThrows;
import utils.JobUtils;

import java.util.List;
import java.util.Objects;

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

    /**
     * 公司规模
     */
    private List<String> scale;

    @SneakyThrows
    public static LagouConfig init() {
        LagouConfig config = JobUtils.getConfig(LagouConfig.class);
        // 转换城市编码
        config.setSalary(Objects.equals("不限", config.getSalary()) ? "0" : config.getSalary());
        List<String> scales = config.getScale();
        config.setScale(scales.stream().map(scale -> "不限".equals(scale) ? "0" : scale).toList());
        return config;
    }

}
