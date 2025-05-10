package ru.spbstu.hsai;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.spbstu.hsai.config.MongoConfig;
import ru.spbstu.hsai.config.SecurityConfig;
import ru.spbstu.hsai.config.WebConfig;
import ru.spbstu.hsai.telegram.BotStarter;
import ru.spbstu.hsai.infrastructure.ServerStarter;
import ru.spbstu.hsai.notification.SchedulerConfig;

import java.util.Arrays;
import java.util.concurrent.*;

@Modulithic
@EnableScheduling
public class Main {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                WebConfig.class,
                MongoConfig.class,
                SecurityConfig.class,
                MongoConfig.class,
                SchedulerConfig.class
        );

        CountDownLatch latch = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            BotStarter bot = context.getBean(BotStarter.class);
            ServerStarter server = context.getBean(ServerStarter.class);

            executor.submit(() -> {
                try {
                    server.start(context);
                } catch (Exception e) {
                    System.err.println("Ошибка сервера: " + e.getMessage());
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    bot.start(context);
                } catch (Exception e) {
                    System.err.println("Ошибка бота: " + Arrays.toString(e.getStackTrace()));
                    latch.countDown();
                }
            });

            latch.await();
            System.out.println("Один из компонентов завершился.");
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
