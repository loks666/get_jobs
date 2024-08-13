package job51;

import lombok.extern.slf4j.Slf4j;
import utils.Bot;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static utils.JobUtils.formatDuration;
import static utils.JobUtils.getDelayTime;

@Slf4j
public class Job51Scheduled {

    public static void main(String[] args) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
        postJobs();
        scheduleTask(scheduler, Job51Scheduled::postJobs);
    }

    private static void postJobs() {
        safeRun(() -> Job51.main(null));
    }

    private static void scheduleTask(ScheduledExecutorService scheduler, Runnable task) {
        long delay = getInitialDelay();
        String msg = "【51job】距离下次投递还有" + formatDuration(delay);
        log.info(msg);
        Bot.sendMessage(msg);
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
