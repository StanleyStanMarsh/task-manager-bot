package ru.spbstu.hsai.telegram.commands;

import ru.spbstu.hsai.telegram.events.UpdateReceivedEvent;

public interface TelegramCommand {
    boolean supports(String command);
    void handle(UpdateReceivedEvent event);
}
