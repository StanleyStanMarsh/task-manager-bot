package ru.spbstu.hsai.api.commands;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.api.UpdateReceivedEvent;
import ru.spbstu.hsai.core.MessageSender;
import ru.spbstu.hsai.usermanagement.UserServiceInterface;

@Component
public class StartCommand implements TelegramCommand {

    private final MessageSender sender;
    private final UserServiceInterface userService;
    private final TimezoneCommand timezoneCommand;

    @Autowired
    public StartCommand(MessageSender sender, UserServiceInterface userService, TimezoneCommand timezoneCommand) {
        this.sender = sender;
        this.userService = userService;
        this.timezoneCommand = timezoneCommand;
    }

    @Override
    public boolean supports(String command) {
        return "/start".equalsIgnoreCase(command);
    }

    @EventListener(condition = "@telegramCommandsExtract.checkCommand(#a0.update, '/start')")
    public void handle(UpdateReceivedEvent event) {
        Message message = event.getUpdate().getMessage();
        User tgUser = message.getFrom();

        // Регистрация пользователя
        userService.registerIfAbsent(
                        tgUser.getId(),
                        tgUser.getUserName(),
                        tgUser.getFirstName(),
                        tgUser.getLastName()
                )
                .then(Mono.just(SendMessage.builder()
                        .chatId(tgUser.getId())
                        .text("""
                      Пожалуйста, выберите ваш часовой пояс из списка ниже и отправьте его название (например, МСК):
                      - МСК-1 (калининградское время)
                      - МСК   (московское время)
                      - МСК+1 (самарское время)
                      - МСК+2 (екатеринбургское время)
                      - МСК+3 (омское время)
                      - МСК+4 (красноярское время)
                      - МСК+5 (иркутское время)
                      - МСК+6 (якутское время)
                      - МСК+7 (владивостокское время)
                      - МСК+8 (магаданское время)
                      - МСК+9 (камчатское время)
                      """)
                        .build()))
                .flatMap(sm -> Mono.fromCompletionStage(sender.sendAsync(sm)))
                .doOnSuccess(v -> {
                    timezoneCommand.addAwaitingTimezoneUser(tgUser.getId());
                    System.out.println("Timezone prompt sent and user added to awaiting list for chatId=" + tgUser.getId());
                })
                .doOnError(e -> System.out.println("Error sending timezone prompt: " + e.getMessage()))
                .subscribe();
    }
}