package lagou;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

public class LagouEnum {

    @Getter
    public enum CityCode {
        NULL("不限", "0"),
        ALL("全国", "0");

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
            return CityCode.valueOf(value);
        }
    }

    @Getter
    public enum Salary {
        NULL("不限", "0");

        private final String name;
        private final String code;

        Salary(String name, String code) {
            this.name = name;
            this.code = code;
        }

        @JsonCreator
        public static Salary forValue(String value) {
            for (Salary salary : Salary.values()) {
                if (salary.name.equals(value)) {
                    return salary;
                }
            }
            return Salary.valueOf(value);
        }
    }

    @Getter
    public enum Scale {
        NULL("不限", "0");

        private final String name;
        private final String code;

        Scale(String name, String code) {
            this.name = name;
            this.code = code;
        }

        @JsonCreator
        public static Scale forValue(String value) {
            for (Scale scale : Scale.values()) {
                if (scale.name.equals(value)) {
                    return scale;
                }
            }
            return Scale.valueOf(value);
        }
    }

}
