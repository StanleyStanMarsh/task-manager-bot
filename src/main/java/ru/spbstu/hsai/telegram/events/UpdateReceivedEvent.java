package ru.spbstu.hsai.telegram.events;

import org.springframework.context.ApplicationEvent;
import org.telegram.telegrambots.meta.api.objects.Update;

public class UpdateReceivedEvent extends ApplicationEvent {
    private final Update update;

    public UpdateReceivedEvent(Object source, Update update) {
        super(source);
        this.update = update;
    }

    public Update getUpdate() {
        return update;
    }
}
