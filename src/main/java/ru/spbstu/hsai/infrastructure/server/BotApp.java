package ru.spbstu.hsai.infrastructure.server;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.spbstu.hsai.infrastructure.integration.telegram.TelegramBotAdapter;

public class BotApp {

    public static void start(AnnotationConfigApplicationContext context) throws TelegramApiException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        TelegramBotAdapter bot = context.getBean(TelegramBotAdapter.class);
        telegramBotsApi.registerBot(bot);
    }
}
