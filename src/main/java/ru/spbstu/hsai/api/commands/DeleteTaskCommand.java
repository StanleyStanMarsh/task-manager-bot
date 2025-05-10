package ru.spbstu.hsai.api.commands;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.api.UpdateReceivedEvent;
import ru.spbstu.hsai.core.MessageSender;
import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTaskInterface;
import ru.spbstu.hsai.simpletaskmanagment.SimpleTaskInterface;
import ru.spbstu.hsai.usermanagement.UserServiceInterface;

@Component
public class DeleteTaskCommand implements TelegramCommand {
    private final MessageSender sender;
    private final UserServiceInterface userService;
    private final SimpleTaskInterface taskService;
    private final RepeatingTaskInterface repeatingTaskService;

    public DeleteTaskCommand(MessageSender sender,
                             UserServiceInterface userService,
                             SimpleTaskInterface taskService,
                             RepeatingTaskInterface repeatingTaskService) {
        this.sender = sender;
        this.userService = userService;
        this.taskService = taskService;
        this.repeatingTaskService = repeatingTaskService;
    }

    @Override
    public boolean supports(String command) {
        return "/deletetask".equalsIgnoreCase(command);
    }

    @EventListener(condition = "@telegramCommandsExtract.checkCommand(#a0.update, '/deletetask')")
    public void handle(UpdateReceivedEvent event) {
        Message message = event.getUpdate().getMessage();
        User tgUser = message.getFrom();
        Long chatId = message.getChatId();
        String text = message.getText();

        // Извлекаем ID из команды
        String IdTask = text.substring("/deletetask".length()).trim();

        if (IdTask.isEmpty()) {
            sender.sendAsync(new SendMessage(chatId.toString(),
                    "❌ Укажите ID задачи. Пример: /deletetask 68189edc1c9de42dfbcc7c79"));
            return;
        }

        userService.findByTelegramId(tgUser.getId())
                .flatMap(user -> {
                    // Пробуем удалить как обычную задачу
                    return taskService.deleteTaskIfBelongsToUser(IdTask, user.getId())
                            .flatMap(deleted -> {
                                if (deleted) {
                                    return Mono.just(true);
                                }
                                // Если не нашли в обычных, пробуем удалить как периодическую
                                return repeatingTaskService.deleteTaskIfBelongsToUser(IdTask, user.getId());
                            });
                })
                .subscribe(deleted -> {
                    if (deleted) {
                        sender.sendAsync(new SendMessage(chatId.toString(), "🌟 Задача успешно удалена!\n\n" +
                                "Если хотите вернуться к списку команд, используйте /help"));
                    } else {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Задача с ID " + IdTask + " не найдена или не принадлежит вам.\n" +
                                        "Проверьте корректность ввода и попробуйте снова."));
                    }
                }, error -> {
                    sender.sendAsync(new SendMessage(chatId.toString(),
                            "❌ Ошибка при удалении задачи: " + error.getMessage()));
                });

    }

}
