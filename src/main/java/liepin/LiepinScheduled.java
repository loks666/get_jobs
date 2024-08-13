package liepin;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static utils.JobUtils.getDelayTime;

@Slf4j
public class LiepinScheduled {

    public static void main(String[] args) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
        postJobs();
        scheduleTask(scheduler, LiepinScheduled::postJobs);
    }

    private static void postJobs() {
        safeRun(() -> Liepin.main(null));
    }

    private static void scheduleTask(ScheduledExecutorService scheduler, Runnable task) {
        long delay = getInitialDelay();
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
