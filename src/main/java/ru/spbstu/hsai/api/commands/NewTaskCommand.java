package ru.spbstu.hsai.api.commands;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.spbstu.hsai.api.events.UpdateReceivedEvent;
import ru.spbstu.hsai.infrastructure.integration.telegram.TelegramSenderService;
import ru.spbstu.hsai.modules.simpletaskmanagment.model.SimpleTask;
import ru.spbstu.hsai.modules.simpletaskmanagment.service.SimpleTaskService;
import ru.spbstu.hsai.modules.usermanagement.service.UserService;

import java.time.LocalDate;

@Component
public class NewTaskCommand implements TelegramCommand {
    private final TelegramSenderService sender;
    private final UserService userService;
    private final SimpleTaskService taskService;

    public NewTaskCommand(TelegramSenderService sender,
                          UserService userService,
                          SimpleTaskService taskService) {
        this.sender = sender;
        this.userService = userService;
        this.taskService = taskService;
    }

    @Override
    public boolean supports(String command) {
        return "/newtask".equalsIgnoreCase(command);
    }


    @EventListener(condition = "@telegramCommandsExtract.checkCommand(#a0.update, '/newtask')")
    public void handle(UpdateReceivedEvent event) {
        Message message = event.getUpdate().getMessage();
        User tgUser = message.getFrom();
        Long chatId = message.getChatId();

        //todo - тут кажется автомат нужно придумать/сервис для хранения контекста для каждого чата

        //пока что добавление тестовой задачи
        // 1. Ищем пользователя по telegramId (Long)
        userService.findByTelegramId(tgUser.getId())
                .flatMap(user -> {
                    // 2. Создаем тестовую задачу с userId (String)
                    SimpleTask testTask = new SimpleTask(
                            user.getId(), // Используем внутренний ID пользователя
                            "Тестовая задача для проверки коллекции",
                            3,
                            LocalDate.now().plusDays(1),
                            SimpleTask.ReminderType.ONE_DAY_BEFORE
                    );

                    return taskService.createTask(
                            testTask.getUserId(),
                            testTask.getDescription(),
                            testTask.getComplexity(),
                            testTask.getDeadline(),
                            testTask.getReminder()
                    );
                })
                .subscribe(
                        task -> {
                            System.out.println("Тестовая задача создана: " + task);
                            sender.sendAsync(new SendMessage(
                                    chatId.toString(),
                                    "✅" + task.toString()
                            ));
                        },
                        error -> {
                            System.err.println("Ошибка: " + error.getMessage());
                            sender.sendAsync(new SendMessage(
                                    chatId.toString(),
                                    "❌ Ошибка при создании задачи: " + error.getMessage()
                            ));
                        }
                );

    }


}
