package ru.spbstu.hsai.api.commands.utils;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component("telegramCommandsExtract")
public class TelegramCommandsExtract {
    public String extractCommand(Update update) {
        return update.getMessage().getText().trim().split(" ")[0];
    }

     public boolean checkCommand(Update update, String command) {
        return command.equals(extractCommand(update));
     }
}
