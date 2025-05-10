package ru.spbstu.hsai.api.commands;

import ru.spbstu.hsai.api.UpdateReceivedEvent;

public interface TelegramCommand {
    boolean supports(String command);
    void handle(UpdateReceivedEvent event);
}
