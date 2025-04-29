package ru.spbstu.hsai.infrastructure.integration.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.spbstu.hsai.api.commands.TelegramCommand;
import ru.spbstu.hsai.infrastructure.server.BotProperties;

import java.util.List;

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
