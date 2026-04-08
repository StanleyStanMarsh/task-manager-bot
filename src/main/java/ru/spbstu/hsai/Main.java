package ru.spbstu.hsai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.spbstu.hsai.config.MongoConfig;
import ru.spbstu.hsai.config.SecurityConfig;
import ru.spbstu.hsai.config.WebConfig;
import ru.spbstu.hsai.telegram.BotStarter;
import ru.spbstu.hsai.infrastructure.ServerStarter;
import ru.spbstu.hsai.notification.SchedulerConfig;
import ru.spbstu.hsai.config.VaultConfiguration;

import java.util.concurrent.*;

@Modulithic
@EnableScheduling
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                VaultConfiguration.class,
                WebConfig.class,
                MongoConfig.class,
                SecurityConfig.class,
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
                    log.error("Ошибка сервера: ", e);
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    bot.start(context);
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
