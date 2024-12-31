import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class StartAll {

    public static void main(String[] args) {
        // Create a ScheduledExecutorService for Boss
        ScheduledExecutorService bossScheduler = Executors.newSingleThreadScheduledExecutor();

        // Define the task for Boss
        Runnable bossTask = () -> {
            try {
                log.info("正在执行 Boss 任务，线程名称: {}", Thread.currentThread().getName());
                executeTask("boss.Boss");
                log.info("Boss 任务已完成，完成时间: {}", java.time.LocalDateTime.now());
            } catch (Exception e) {
                log.error("Boss 任务执行过程中发生错误: {}", e.getMessage(), e);
            }
        };

        // Schedule Boss task to run every 60 minutes
        bossScheduler.scheduleAtFixedRate(bossTask, 0, 60, TimeUnit.MINUTES);

        // Start Liepin and Job51 tasks in separate processes
        new Thread(() -> {
            try {
                log.info("正在执行 Liepin 任务，线程名称: {}", Thread.currentThread().getName());
                executeTask("liepin.Liepin");
                log.info("Liepin 任务已完成，完成时间: {}", java.time.LocalDateTime.now());
            } catch (Exception e) {
                log.error("Liepin 任务执行过程中发生错误: {}", e.getMessage(), e);
            }
        }).start();

        new Thread(() -> {
            try {
                log.info("正在执行 Job51 任务，线程名称: {}", Thread.currentThread().getName());
                executeTask("job51.Job51");
                log.info("Job51 任务已完成，完成时间: {}", java.time.LocalDateTime.now());
            } catch (Exception e) {
                log.error("Job51 任务执行过程中发生错误: {}", e.getMessage(), e);
            }
        }).start();

        // Add a shutdown hook to gracefully shut down the scheduler
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭调度器...");
            bossScheduler.shutdown();
            try {
                if (!bossScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("强制关闭 Boss 调度器...");
                    bossScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭调度器时发生错误: {}", e.getMessage(), e);
                bossScheduler.shutdownNow();
            }
        }));
    }

    /**
     * 使用独立进程运行指定的类
     *
     * @param className 要执行的类名
     * @throws Exception 如果发生错误
     */
    private static void executeTask(String className) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
            "java", "-cp", System.getProperty("java.class.path"), className
        );
        processBuilder.inheritIO(); // 将子进程的输入/输出重定向到当前进程
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException(className + " 执行失败，退出代码: " + exitCode);
        }
    }
}
