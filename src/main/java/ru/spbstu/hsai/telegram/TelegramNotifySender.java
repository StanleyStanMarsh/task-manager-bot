package ru.spbstu.hsai.telegram;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;

@Component
public class TelegramNotifySender {
    private final MessageSender sender;

    public TelegramNotifySender(MessageSender sender) {
        this.sender = sender;
    }

    public Mono<Void> sendNotification(long telegramId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(telegramId)
                .text(text)
                .build();
        message.enableHtml(true);

        return Mono.fromCompletionStage(sender.sendAsync(message)).then();
    }
}
