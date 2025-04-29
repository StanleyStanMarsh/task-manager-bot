package ru.spbstu.hsai.api.commands;

import org.springframework.context.event.EventListener;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.spbstu.hsai.api.events.UpdateReceivedEvent;
import ru.spbstu.hsai.infrastructure.integration.telegram.TelegramSenderService;

@Component
public class StartCommand implements TelegramCommand {

    private final TelegramSenderService sender;

    public StartCommand(TelegramSenderService sender) {
        this.sender = sender;
    }

    @Override
    public boolean supports(String command) {
        return "/start".equalsIgnoreCase(command);
    }

    @EventListener(condition = "@telegramCommandsExtract.checkCommand(#a0.update, '/start')")
    public void handle(UpdateReceivedEvent event) {
        Message message = event.getUpdate().getMessage();
        User user = message.getFrom();
        SendMessage sm = SendMessage.builder()
                .chatId(user.getId())
                .text(String.format(
                        "\uD83C\uDF89 Добро пожаловать, %s!%nИспользуйте /help для списка команд.",
                        user.getUserName()
                ))
                .build();
        sender.send(sm);
        // TODO сделать логику добавления пользователя в бд
    }
}
