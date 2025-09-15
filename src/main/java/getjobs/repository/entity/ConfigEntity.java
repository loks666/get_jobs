package getjobs.repository.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * Boss 配置实体（字段与 BossConfig 对齐）
 */
@Data
@Entity
@Table(name = "boss_config")
@EqualsAndHashCode(callSuper = true)
public class ConfigEntity extends BaseEntity {

    @Column(name = "say_hi", columnDefinition = "TEXT")
    private String sayHi;

    @Convert(converter = JsonListStringConverter.class)
    @Column(name = "keywords", columnDefinition = "TEXT")
    private List<String> keywords;

    @Convert(converter = JsonListStringConverter.class)
    @Column(name = "city_code", columnDefinition = "TEXT")
    private List<String> cityCode;

    @Convert(converter = JsonMapStringConverter.class)
    @Column(name = "custom_city_code", columnDefinition = "TEXT")
    private Map<String, String> customCityCode;

    @Convert(converter = JsonListStringConverter.class)
    @Column(name = "industry", columnDefinition = "TEXT")
    private List<String> industry;

    @Convert(converter = JsonListStringConverter.class)
    @Column(name = "experience", columnDefinition = "TEXT")
    private List<String> experience;

    @Column(name = "job_type", length = 50)
    private String jobType;

    @Column(name = "salary", length = 50)
    private String salary;

    @Column(name = "expected_position", length = 200)
    private String expectedPosition;

    @Convert(converter = JsonListStringConverter.class)
    @Column(name = "degree", columnDefinition = "TEXT")
    private List<String> degree;

    @Convert(converter = JsonListStringConverter.class)
    @Column(name = "scale", columnDefinition = "TEXT")
    private List<String> scale;

    @Convert(converter = JsonListStringConverter.class)
    @Column(name = "stage", columnDefinition = "TEXT")
    private List<String> stage;

    @Column(name = "enable_ai_job_match_detection")
    private Boolean enableAIJobMatchDetection;

    @Column(name = "enable_ai_greeting")
    private Boolean enableAIGreeting;

    @Column(name = "filter_dead_hr")
    private Boolean filterDeadHR;

    @Column(name = "send_img_resume")
    private Boolean sendImgResume;

    @Column(name = "resume_image_path")
    private String resumeImagePath;

    @Column(name = "resume_content", columnDefinition = "TEXT")
    private String resumeContent;

    @Convert(converter = JsonListIntegerConverter.class)
    @Column(name = "expected_salary", columnDefinition = "TEXT")
    private List<Integer> expectedSalary;

    @Column(name = "wait_time", length = 50)
    private String waitTime;

    @Convert(converter = JsonListStringConverter.class)
    @Column(name = "dead_status", columnDefinition = "TEXT")
    private List<String> deadStatus;

    @Column(name = "key_filter")
    private Boolean keyFilter;

    @Column(name = "recommend_jobs")
    private Boolean recommendJobs;

    @Column(name = "check_state_owned")
    private Boolean checkStateOwned;

    @Column(name = "cookie_data", columnDefinition = "TEXT")
    private String cookieData;

}
