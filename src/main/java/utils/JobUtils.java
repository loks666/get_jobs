package utils;

import static utils.Constant.UNLIMITED_CODE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import boss.BossScheduled;


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
    public static <T> T getConfig(Class<T> clazz) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        
        // 优先从工作目录的getjobs目录读取配置文件
        String getjobsConfigPath = System.getProperty("user.dir") + "/getjobs/config.yaml";
        java.io.File getjobsConfigFile = new java.io.File(getjobsConfigPath);
        
        InputStream is = null;
        if (getjobsConfigFile.exists()) {
            try {
                is = new java.io.FileInputStream(getjobsConfigFile);
                log.debug("从getjobs目录读取配置文件: {}", getjobsConfigPath);
            } catch (Exception e) {
                log.warn("从getjobs目录读取配置文件失败，将使用resources目录: {}", e.getMessage());
                is = null;
            }
        }
        
        // 如果getjobs目录的配置文件不存在或读取失败，回退到resources目录
        if (is == null) {
            is = clazz.getClassLoader().getResourceAsStream("config.yaml");
            if (is == null) {
                throw new FileNotFoundException("无法找到 config.yaml 文件，请检查getjobs目录或resources目录");
            }
            log.debug("从resources目录读取配置文件");
        }
        
        try (InputStream inputStream = is) {
            JsonNode rootNode = mapper.readTree(inputStream);
            String key = clazz.getSimpleName().toLowerCase().replaceAll("config", "");
            JsonNode configNode = rootNode.path(key);
            return mapper.treeToValue(configNode, clazz);
        }
    }

    public static void runScheduled(Platform platform) {
        String platformName = platform.getPlatformName();
        switch (platform) {
            case BOSS -> {
                BossScheduled.postJobs();
                scheduleTaskAtTime(platformName, 10, 0, BossScheduled::postJobs);
                scheduleTaskAtTime(platformName, 15, 0, BossScheduled::postJobs);
            }
            case JOB51 -> {
              
            }
            case LIEPIN -> {
              
            }
            case ZHILIAN -> {
             
            }
            case LAGOU -> {
              
            }
            default -> log.warn("未定义的平台任务：{}", platformName);
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


    /**
     * 通用的任务调度方法
     *
     * @param hour   要设置的小时，0-23之间的整数
     * @param minute 要设置的分钟，0-59之间的整数
     */
    public static void scheduleTaskAtTime(String platform, int hour, int minute, Runnable task) {
        long delay = getInitialDelay(hour, minute);  // 计算初始延迟
        String msg = String.format("【%s】距离下次任务投递还有：%s，执行时间：%02d:%02d", platform, formatDuration(delay), hour, minute);
        log.info(msg);
        Bot.sendMessage(msg);

        // 安排定时任务，每24小时执行一次
        Executors.newScheduledThreadPool(4).scheduleAtFixedRate(task, delay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
    }

    /**
     * 计算从当前时间到指定时间（小时:分钟）的延迟
     *
     * @param targetHour   目标执行的小时
     * @param targetMinute 目标执行的分钟
     * @return 延迟的秒数
     */
    public static long getInitialDelay(int targetHour, int targetMinute) {
        Calendar now = Calendar.getInstance();
        Calendar nextRun = Calendar.getInstance();

        // 设置目标时间
        nextRun.set(Calendar.HOUR_OF_DAY, targetHour);
        nextRun.set(Calendar.MINUTE, targetMinute);
        nextRun.set(Calendar.SECOND, 0);
        nextRun.set(Calendar.MILLISECOND, 0);

        // 如果当前时间已经过了今天的目标时间，则将任务安排在明天
        if (now.after(nextRun)) {
            nextRun.add(Calendar.DAY_OF_YEAR, 1);  // 调整为明天
        }

        long currentTime = System.currentTimeMillis();
        return (nextRun.getTimeInMillis() - currentTime) / 1000;  // 返回秒数
    }

    public static int getRandomNumberInRange(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("max must be greater than or equal to min");
        }
        Random random = new Random();
        return random.nextInt((max - min) + 1) + min;
    }

}
