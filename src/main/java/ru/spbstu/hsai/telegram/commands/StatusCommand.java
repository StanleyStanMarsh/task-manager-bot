package ru.spbstu.hsai.telegram.commands;

import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.telegram.events.UpdateReceivedEvent;
import ru.spbstu.hsai.telegram.MessageSender;
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
    private final ReactiveMongoTemplate mongoTemplate;


    public StatusCommand(MessageSender sender,
                         UserServiceInterface userService,
                         SimpleTaskInterface taskService,
                         RepeatingTaskInterface repeatingTaskService,
                         ReactiveMongoTemplate mongoTemplate) {
        this.sender = sender;
        this.userService = userService;
        this.taskService = taskService;
        this.repeatingTaskService = repeatingTaskService;
        this.mongoTemplate = mongoTemplate; //todo - пока так, надо реализовать проверку
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

        Mono<Boolean> mongoStatusMono = checkMongoConnection();

        userService.findByTelegramId(tgUser.getId())
                .flatMap(user -> {
                    Mono<List<SimpleTask>> simpleActiveTasks = taskService.getActiveTasks(user.getId())
                            .collectList();
                    Mono<List<SimpleTask>> simpleCompletedTasks = taskService.getCompletedTasks(user.getId())
                            .collectList();
                    Mono<List<RepeatingTask>> repeatingTasks = repeatingTaskService.getActiveTasks(user.getId())
                            .collectList();

                    return Mono.zip(simpleActiveTasks, simpleCompletedTasks, repeatingTasks, mongoStatusMono)
                            .map(tuple -> {
                                int simpleActiveCount = tuple.getT1().size();
                                int completedCount = tuple.getT2().size();
                                int repeatingCount = tuple.getT3().size();
                                int totalActive = simpleActiveCount + repeatingCount;
                                boolean isConnected = tuple.getT4();

                                return statusMessage(totalActive, completedCount, isConnected);
                            });
                })
                .subscribe(
                        statusMessage -> sender.sendAsync(new SendMessage(chatId.toString(), statusMessage)),
                        error -> sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Ошибка при получении статуса: " + error.getMessage()))
                );
    }


    private String statusMessage(int activeTasks, int completedTasks, boolean isConnected) {
        return """
               📍 Статус Бота:
               
               🔌 Подключение: %s
               📋 Активных задач: %d
               ✅ Завершенных задач: %d
               
               Используйте /help для списка команд
               """.formatted(
                isConnected ? "установлено" : "не установлено",
                activeTasks,
                completedTasks
        );
    }

    private Mono<Boolean> checkMongoConnection() {
        return mongoTemplate.collectionExists("users")
                .onErrorReturn(false);
    }

}