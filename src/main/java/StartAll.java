import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class StartAll {

    public static void main(String[] args) {
        // 创建一个统一的线程池来执行所有任务
        ExecutorService executorService = Executors.newFixedThreadPool(3);

        // 定义Boss任务
        Runnable bossTask = () -> {
            try {
                log.info("正在执行 Boss 任务，线程名称: {}", Thread.currentThread().getName());
                executeTask("boss.MobileBoss");
                log.info("Boss 任务已完成，完成时间: {}", java.time.LocalDateTime.now());
            } catch (Exception e) {
                log.error("Boss 任务执行过程中发生错误: {}", e.getMessage(), e);
            }
        };

        // 定义Liepin任务
        Runnable liepinTask = () -> {
            try {
                log.info("正在执行 Liepin 任务，线程名称: {}", Thread.currentThread().getName());
                executeTask("liepin.Liepin");
                log.info("Liepin 任务已完成，完成时间: {}", java.time.LocalDateTime.now());
            } catch (Exception e) {
                log.error("Liepin 任务执行过程中发生错误: {}", e.getMessage(), e);
            }
        };

        // 定义Job51任务
        Runnable job51Task = () -> {
            try {
                log.info("正在执行 Job51 任务，线程名称: {}", Thread.currentThread().getName());
                executeTask("job51.Job51");
                log.info("Job51 任务已完成，完成时间: {}", java.time.LocalDateTime.now());
            } catch (Exception e) {
                log.error("Job51 任务执行过程中发生错误: {}", e.getMessage(), e);
            }
        };

        // 提交所有任务到线程池执行
        executorService.submit(bossTask);
//        executorService.submit(liepinTask);
//        executorService.submit(job51Task);

        // 添加关闭钩子，优雅地关闭线程池
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭线程池...");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("强制关闭线程池...");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭线程池时发生错误: {}", e.getMessage(), e);
                executorService.shutdownNow();
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
