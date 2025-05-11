package ru.spbstu.hsai.telegram.settings;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.telegram.MessageSender;

import java.util.concurrent.CompletableFuture;

@Service
public class TelegramSenderService implements MessageSender {
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
    public CompletableFuture<Message> sendAsync(SendMessage message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sender.execute(message);
            } catch (TelegramApiException e) {
                throw new RuntimeException("Failed to send message", e);
            }
        });
    }
    public Mono<Message> sendReactive(SendMessage message) {
        return Mono.fromFuture(sendAsync(message));
    }
}
