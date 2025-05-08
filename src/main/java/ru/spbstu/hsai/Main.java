package ru.spbstu.hsai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import ru.spbstu.hsai.infrastructure.server.ServerApp;
import ru.spbstu.hsai.infrastructure.server.BotApp;

import java.util.concurrent.*;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ru.spbstu.hsai.infrastructure.config.VaultConfiguration.class,
                ru.spbstu.hsai.infrastructure.config.WebConfig.class,
                ru.spbstu.hsai.infrastructure.config.MongoConfig.class,
                ru.spbstu.hsai.infrastructure.config.SecurityConfig.class
        );

        CountDownLatch latch = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            executor.submit(() -> {
                try {
                    ServerApp.start(context);
                } catch (Exception e) {
                    log.error("Ошибка сервера: ", e);
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    BotApp.start(context);
                } catch (Exception e) {
                    log.error("Ошибка бота: ", e);
                    latch.countDown();
                }
            });

            latch.await();
            log.warn("Один из компонентов завершился.");
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
