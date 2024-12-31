import boss.Boss;
import job51.Job51;
import liepin.Liepin;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class StartAll {

    public static void main(String[] args) {
        // Create a ScheduledExecutorService for Boss
        ScheduledExecutorService bossScheduler = Executors.newSingleThreadScheduledExecutor();

        // Create a separate ExecutorService for Liepin and Job51
        ScheduledExecutorService liepinJobScheduler = Executors.newScheduledThreadPool(2);

        // Define the task for Boss
        Runnable bossTask = () -> {
            try {
                log.info("正在执行 Boss 任务，线程名称: {}", Thread.currentThread().getName());
                Boss.main(null);
                log.info("Boss 任务已完成，完成时间: {}", java.time.LocalDateTime.now());
            } catch (Exception e) {
                log.error("Boss 任务执行过程中发生错误: {}", e.getMessage(), e);
            }
        };

        // Define the task for Liepin
        Runnable liepinTask = () -> {
            try {
                log.info("正在执行 Liepin 任务，线程名称: {}", Thread.currentThread().getName());
                Liepin.main(null);
                log.info("Liepin 任务已完成，完成时间: {}", java.time.LocalDateTime.now());
            } catch (Exception e) {
                log.error("Liepin 任务执行过程中发生错误: {}", e.getMessage(), e);
            }
        };

        // Define the task for Job51
        Runnable job51Task = () -> {
            try {
                log.info("正在执行 Job51 任务，线程名称: {}", Thread.currentThread().getName());
                Job51.main(null);
                log.info("Job51 任务已完成，完成时间: {}", java.time.LocalDateTime.now());
            } catch (Exception e) {
                log.error("Job51 任务执行过程中发生错误: {}", e.getMessage(), e);
            }
        };

        // Schedule Boss task to run every 30 minutes
        bossScheduler.scheduleAtFixedRate(bossTask, 0, 30, TimeUnit.MINUTES);

        // Schedule Liepin and Job51 tasks to run immediately in separate threads
        liepinJobScheduler.submit(liepinTask);
        liepinJobScheduler.submit(job51Task);

        // Add a shutdown hook to gracefully shut down the schedulers
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭调度器...");
            bossScheduler.shutdown();
            liepinJobScheduler.shutdown();
            try {
                if (!bossScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("强制关闭 Boss 调度器...");
                    bossScheduler.shutdownNow();
                }
                if (!liepinJobScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("强制关闭 Liepin/Job51 调度器...");
                    liepinJobScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭调度器时发生错误: {}", e.getMessage(), e);
                bossScheduler.shutdownNow();
                liepinJobScheduler.shutdownNow();
            }
        }));
    }
}
