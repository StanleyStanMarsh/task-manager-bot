package ru.spbstu.hsai.api.commands.notifier;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.infrastructure.integration.telegram.TelegramSenderService;

@Component
public class TelegramNotifySender {
    private final TelegramSenderService sender;

    public TelegramNotifySender(TelegramSenderService sender) {
        this.sender = sender;
    }

    public Mono<Void> sendNotification(long telegramId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(telegramId)
                .text(text)
                .build();

        return Mono.fromCompletionStage(sender.sendAsync(message)).then();
    }
}
