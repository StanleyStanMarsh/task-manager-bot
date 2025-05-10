package ru.spbstu.hsai.telegram.settings;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.spbstu.hsai.telegram.events.UpdateReceivedEvent;

@Component
public class UpdateReceiveService {
    private final ApplicationEventPublisher publisher;

    public UpdateReceiveService(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void updateHandle(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            publisher.publishEvent(new UpdateReceivedEvent(this, update));
        }
    }
}
