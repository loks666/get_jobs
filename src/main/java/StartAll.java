import boss.Boss;
import job51.Job51;
import lagou.Lagou;
import liepin.Liepin;
import lombok.extern.slf4j.Slf4j;
import zhilian.ZhiLian;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static utils.JobUtils.getDelayTime;


@Slf4j
public class StartAll {

    public static void main(String[] args) {
        // 创建一个调度任务的服务，线程池大小为4，确保任务按顺序执行
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

        // 立即执行
        runAllPlatforms();

        // 调度执行
        scheduleTask(scheduler, StartAll::runAllPlatforms);
    }

    private static void runAllPlatforms() {
        safeRun(() -> Boss.main(null));
        safeRun(() -> Liepin.main(null));
        safeRun(() -> ZhiLian.main(null));
        safeRun(() -> Job51.main(null));
        safeRun(() -> Lagou.main(null));
    }

    private static void scheduleTask(ScheduledExecutorService scheduler, Runnable task) {
        long delay = getInitialDelay();

        // 设置定时任务，每天8点执行一次
        scheduler.scheduleAtFixedRate(task, delay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
    }

    private static long getInitialDelay() {
        return getDelayTime();
    }

    private static void safeRun(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("safeRun异常：{}", e.getMessage(), e);
        }
    }
}
