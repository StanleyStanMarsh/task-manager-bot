package ru.spbstu.hsai.infrastructure.integration.telegram;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.hsai.infrastructure.server.BotProperties;

@Component
public class TelegramBotAdapter extends TelegramLongPollingBot {

    String token;
    String username;
    private final UpdateReceiveService updateService;

    public TelegramBotAdapter(BotProperties botProperties, UpdateReceiveService updateService) {
        this.token = botProperties.token();
        this.username = botProperties.botUsername();
        this.updateService = updateService;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        updateService.updateHandle(update);
    }
}
