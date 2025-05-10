package ru.spbstu.hsai.telegram.settings;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.spbstu.hsai.telegram.BotStarter;

@Component
public class BotApp implements BotStarter {

    public void start(AnnotationConfigApplicationContext context) throws TelegramApiException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        TelegramBotAdapter bot = context.getBean(TelegramBotAdapter.class);
        telegramBotsApi.registerBot(bot);
    }
}
