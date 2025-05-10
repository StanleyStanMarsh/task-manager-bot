package ru.spbstu.hsai.infrastructure;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public interface BotStarter {
    void start(AnnotationConfigApplicationContext context) throws TelegramApiException;
}
