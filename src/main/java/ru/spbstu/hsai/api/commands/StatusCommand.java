package ru.spbstu.hsai.api.commands;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.api.UpdateReceivedEvent;
import ru.spbstu.hsai.core.MessageSender;
import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTask;
import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTaskInterface;
import ru.spbstu.hsai.simpletaskmanagment.SimpleTask;
import ru.spbstu.hsai.simpletaskmanagment.SimpleTaskInterface;
import ru.spbstu.hsai.usermanagement.UserServiceInterface;

import java.util.List;


@Component
public class StatusCommand implements TelegramCommand {
    private final MessageSender sender;
    private final UserServiceInterface userService;
    private final SimpleTaskInterface taskService;
    private final RepeatingTaskInterface repeatingTaskService;
    private final boolean isConnectionEstablished;

    public StatusCommand(MessageSender sender,
                         UserServiceInterface userService,
                         SimpleTaskInterface taskService,
                         RepeatingTaskInterface repeatingTaskService) {
        this.sender = sender;
        this.userService = userService;
        this.taskService = taskService;
        this.repeatingTaskService = repeatingTaskService;
        this.isConnectionEstablished = true; //todo - пока так, надо реализовать проверку
    }

    @Override
    public boolean supports(String command) {
        return "/status".equalsIgnoreCase(command);
    }


    @EventListener(condition = "@telegramCommandsExtract.checkCommand(#a0.update, '/status')")
    public void handle(UpdateReceivedEvent event) {
        Message message = event.getUpdate().getMessage();
        User tgUser = message.getFrom();
        Long chatId = message.getChatId();

        userService.findByTelegramId(tgUser.getId())
                .flatMap(user -> {
                    Mono<List<SimpleTask>> simpleActiveTasks = taskService.getActiveTasks(user.getId())
                            .collectList();
                    Mono<List<SimpleTask>> simpleCompletedTasks = taskService.getCompletedTasks(user.getId())
                            .collectList();
                    Mono<List<RepeatingTask>> repeatingTasks = repeatingTaskService.getActiveTasks(user.getId())
                            .collectList();

                    return Mono.zip(simpleActiveTasks, simpleCompletedTasks, repeatingTasks)
                            .map(tuple -> {
                                int simpleActiveCount = tuple.getT1().size();
                                int completedCount = tuple.getT2().size();
                                int repeatingCount = tuple.getT3().size();
                                int totalActive = simpleActiveCount + repeatingCount;

                                return statusMessage(totalActive, completedCount);
                            });
                })
                .subscribe(
                        statusMessage -> sender.sendAsync(new SendMessage(chatId.toString(), statusMessage)),
                        error -> sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Ошибка при получении статуса: " + error.getMessage()))
                );
    }


    private String statusMessage(int activeTasks, int completedTasks) {
        return """
               📍 Статус Бота:
               
               🔌 Подключение: %s
               📋 Активных задач: %d
               ✅ Завершенных задач: %d
               
               Используйте /help для списка команд
               """.formatted(
                isConnectionEstablished ? "установлено" : "не установлено",
                activeTasks,
                completedTasks
        );
    }

}