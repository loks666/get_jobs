package job51;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 * 前程无忧自动投递简历
 */
public class Job51Enum {

    @Getter
    public enum jobArea {
        NULL("不限", "0"),
        BEIJING("北京", "010000"),
        SHANGHAI("上海", "020000"),
        GUANGZHOU("广州", "030200"),
        SHENZHEN("深圳", "040000"),
        WUHAN("武汉", "180200"),
        CHENGDU("成都", "090200");
        private final String name;
        private final String code;

        jobArea(String name, String code) {
            this.name = name;
            this.code = code;
        }

        @JsonCreator
        public static jobArea forValue(String value) {
            for (jobArea cityCode : jobArea.values()) {
                if (cityCode.name.equals(value)) {
                    return cityCode;
                }
            }
            return NULL;
        }

    }

    @Getter
    public enum Salary {
        NULL("不限", "0"),
        BELOW_2K("2千以下", "01"),
        FROM_2K_TO_3K("2-3千", "02"),
        FROM_3K_TO_4_5K("3-4.5千", "03"),
        FROM_4_5K_TO_6K("4.5-6千", "04"),
        FROM_6K_TO_8K("6-8千", "05"),
        FROM_8K_TO_10K("0.8-1万", "06"),
        FROM_10K_TO_15K("1-1.5万", "07"),
        FROM_15K_TO_20K("1.5-2万", "08"),
        FROM_20K_TO_30K("2-3万", "09"),
        FROM_30K_TO_40K("3-4万", "10"),
        FROM_40K_TO_50K("4-5万", "11"),
        ABOVE_50K("5万以上", "12");

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
            return NULL;
        }
    }

}
