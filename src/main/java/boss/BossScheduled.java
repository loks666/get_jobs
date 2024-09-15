package boss;

import lombok.extern.slf4j.Slf4j;
import utils.JobUtils;
import utils.Platform;

@Slf4j
public class BossScheduled {

    public static void main(String[] args) {
        JobUtils.runScheduled(Platform.BOSS);
    }

    public static void postJobs() {
        safeRun(() -> Boss.main(null));
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
