package ru.spbstu.hsai.telegram.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.spbstu.hsai.telegram.BotStarter;

@Component
public class BotApp implements BotStarter {

    private static final Logger log = LoggerFactory.getLogger(BotApp.class);

    public void start(AnnotationConfigApplicationContext context) throws TelegramApiException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        TelegramBotAdapter bot = context.getBean(TelegramBotAdapter.class);
        try {
            telegramBotsApi.registerBot(bot);
        } catch (TelegramApiException e) {
            log.warn("Telegram bot не зарегистрирован (токен/сеть). HTTP API продолжает работу: {}", e.getMessage());
        }
    }
}
