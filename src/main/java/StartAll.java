import boss.Boss;
import job51.Job51;
import liepin.Liepin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StartAll {

    public static void main(String[] args) {
        // Create a ScheduledExecutorService for Boss
        ScheduledExecutorService bossScheduler = Executors.newSingleThreadScheduledExecutor();

        // Create a separate ExecutorService for Liepin and Job51
        ScheduledExecutorService liepinJobScheduler = Executors.newScheduledThreadPool(2);

        // Define the task for Boss
        Runnable bossTask = () -> {
            try {
                System.out.println("Running Boss task in thread: " + Thread.currentThread().getName());
                Boss.main(null);
                System.out.println("Boss task completed at: " + java.time.LocalDateTime.now());
            } catch (Exception e) {
                System.err.println("An error occurred in Boss task: " + e.getMessage());
                e.printStackTrace();
            }
        };

        // Define the task for Liepin
        Runnable liepinTask = () -> {
            try {
                System.out.println("Running Liepin task in thread: " + Thread.currentThread().getName());
                Liepin.main(null);
                System.out.println("Liepin task completed at: " + java.time.LocalDateTime.now());
            } catch (Exception e) {
                System.err.println("An error occurred in Liepin task: " + e.getMessage());
            }
        };

        // Define the task for Job51
        Runnable job51Task = () -> {
            try {
                System.out.println("Running Job51 task in thread: " + Thread.currentThread().getName());
                Job51.main(null);
                System.out.println("Job51 task completed at: " + java.time.LocalDateTime.now());
            } catch (Exception e) {
                System.err.println("An error occurred in Job51 task: " + e.getMessage());
            }
        };

        // Schedule Boss task to run every 30 minutes
        bossScheduler.scheduleAtFixedRate(bossTask, 0, 60, TimeUnit.MINUTES);

        // Schedule Liepin and Job51 tasks to run immediately in separate threads
        liepinJobScheduler.submit(liepinTask);
        liepinJobScheduler.submit(job51Task);

        // Add a shutdown hook to gracefully shut down the schedulers
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down schedulers...");
            bossScheduler.shutdown();
            liepinJobScheduler.shutdown();
            try {
                if (!bossScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.out.println("Forcefully shutting down Boss scheduler...");
                    bossScheduler.shutdownNow();
                }
                if (!liepinJobScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.out.println("Forcefully shutting down Liepin/Job51 scheduler...");
                    liepinJobScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                bossScheduler.shutdownNow();
                liepinJobScheduler.shutdownNow();
            }
        }));
    }
}
