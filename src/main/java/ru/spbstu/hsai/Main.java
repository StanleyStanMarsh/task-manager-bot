package ru.spbstu.hsai;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import ru.spbstu.hsai.infrastructure.server.ServerApp;
import ru.spbstu.hsai.infrastructure.server.BotApp;

import java.util.Arrays;
import java.util.concurrent.*;

public class Main {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ru.spbstu.hsai.infrastructure.config.WebConfig.class
        );

        CountDownLatch latch = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            executor.submit(() -> {
                try {
                    ServerApp.start(context);
                } catch (Exception e) {
                    System.err.println("Ошибка сервера: " + e.getMessage());
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    BotApp.start(context);
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
