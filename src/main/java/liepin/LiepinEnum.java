package liepin;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
public class LiepinEnum {

    @Getter
    public enum CityCode {
        NULL("不限", "0"),
        ALL("全国", "410"),
        BEIJING("北京", "010"),
        SHANGHAI("上海", "020"),
        GUANGZHOU("广州", "050020"),
        SHENZHEN("深圳", "050090"),
        WUHAN("武汉", "170020"),
        CHENGDU("成都", "280020");

        private final String name;
        private final String code;

        CityCode(String name, String code) {
            this.name = name;
            this.code = code;
        }

        @JsonCreator
        public static CityCode forValue(String value) {
            for (CityCode cityCode : CityCode.values()) {
                if (cityCode.name.equals(value)) {
                    return cityCode;
                }
            }
            return NULL;
        }

    }

}
