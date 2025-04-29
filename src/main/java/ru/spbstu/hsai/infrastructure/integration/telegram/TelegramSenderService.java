package ru.spbstu.hsai.infrastructure.integration.telegram;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
public class TelegramSenderService {
    private final DefaultAbsSender sender;

    public TelegramSenderService(TelegramBotAdapter botAdapter) {
        this.sender = botAdapter;
    }

    public Message send(SendMessage message) {
        try {
            return sender.execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException("Failed to send message", e);
        }
    }
}
