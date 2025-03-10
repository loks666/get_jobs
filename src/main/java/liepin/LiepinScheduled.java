package liepin;

import lombok.extern.slf4j.Slf4j;
import utils.JobUtils;
import utils.Platform;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
public class LiepinScheduled {

    public static void main(String[] args) {
        JobUtils.runScheduled(Platform.LIEPIN);
    }

    public static void postJobs() {
        safeRun(() -> Liepin.main(null));
    }

    // 任务执行的安全包装，防止异常
    private static void safeRun(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("safeRun异常：{}", e.getMessage(), e);
        }
    }
}
