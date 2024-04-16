package utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.SneakyThrows;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static utils.Constant.UNLIMITED_CODE;

public class JobUtils {

    public static String appendParam(String name, String value) {
        return Optional.ofNullable(value)
                .filter(v -> !Objects.equals(UNLIMITED_CODE, v))
                .map(v -> "&" + name + "=" + v)
                .orElse("");
    }

    public static String appendListParam(String name, List<String> values) {
        return Optional.ofNullable(values)
                .filter(list -> !list.isEmpty() && !Objects.equals(UNLIMITED_CODE, list.get(0)))
                .map(list -> "&" + name + "=" + String.join(",", list))
                .orElse("");
    }

    @SneakyThrows
    public static <T> T getConfig(Class<T> clazz) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        InputStream is = clazz.getClassLoader().getResourceAsStream("config.yaml");
        if (is == null) {
            throw new FileNotFoundException("无法找到 config.yaml 文件");
        }
        JsonNode rootNode = mapper.readTree(is);
        String key = clazz.getSimpleName().toLowerCase().replaceAll("config", "");
        JsonNode configNode = rootNode.path(key);
        return mapper.treeToValue(configNode, clazz);
    }
}
