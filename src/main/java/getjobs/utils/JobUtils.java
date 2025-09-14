package getjobs.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import getjobs.dto.BossConfigDTO;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static getjobs.utils.Constant.UNLIMITED_CODE;

@Slf4j
public class JobUtils {

    public static String appendParam(String name, String value) {
        return Optional.ofNullable(value)
                .filter(v -> !Objects.equals(UNLIMITED_CODE, v))
                .map(v -> "&" + name + "=" + v)
                .orElse("");
    }

    public static String appendListParam(String name, List<String> values) {
        return Optional.ofNullable(values)
                .filter(list -> !list.isEmpty() && !Objects.equals(UNLIMITED_CODE, list.getFirst()))
                .map(list -> "&" + name + "=" + String.join(",", list))
                .orElse("");
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T getConfig(Class<T> clazz) {
        // 如果是BossConfigDTO，直接从数据库查询
        if (BossConfigDTO.class.equals(clazz)) {
            return (T) BossConfigDTO.getInstance();
        }

        // 其他配置类仍然从文件读取（保持向后兼容）
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        // 使用统一的配置文件读取方法
        InputStream is = ConfigFileUtil.getConfigInputStream();
        log.debug("从统一路径读取配置文件");

        try (InputStream inputStream = is) {
            JsonNode rootNode = mapper.readTree(inputStream);
            String key = clazz.getSimpleName().toLowerCase().replaceAll("config", "");
            JsonNode configNode = rootNode.path(key);
            return mapper.treeToValue(configNode, clazz);
        }
    }

    /**
     * 计算并格式化时间（毫秒）
     *
     * @param startDate 开始时间
     * @param endDate   结束时间
     * @return 格式化后的时间字符串，格式为 "HH:mm:ss"
     */
    public static String formatDuration(Date startDate, Date endDate) {
        long durationMillis = endDate.getTime() - startDate.getTime();
        long seconds = (durationMillis / 1000) % 60;
        long minutes = (durationMillis / (1000 * 60)) % 60;
        long hours = durationMillis / (1000 * 60 * 60);
        return String.format("%d时%d分%d秒", hours, minutes, seconds);
    }

    /**
     * 将给定的毫秒时间戳转换为格式化的时间字符串
     *
     * @param durationSeconds 持续时间的时间戳（秒）
     * @return 格式化后的时间字符串，格式为 "HH:mm:ss"
     */
    public static String formatDuration(long durationSeconds) {
        long seconds = durationSeconds % 60;
        long minutes = (durationSeconds / 60) % 60;
        long hours = durationSeconds / 3600; // 直接计算总小时数

        return String.format("%d时%d分%d秒", hours, minutes, seconds);
    }


}
