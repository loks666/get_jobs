package job51;

import lombok.extern.slf4j.Slf4j;
import utils.JobUtils;
import utils.Platform;

@Slf4j
public class Job51Scheduled {

    public static void main(String[] args) {
        JobUtils.runScheduled(Platform.JOB51);
    }

    public static void postJobs() {
        safeRun(() -> Job51.main(null));
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
