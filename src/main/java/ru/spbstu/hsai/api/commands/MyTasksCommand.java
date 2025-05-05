package ru.spbstu.hsai.api.commands;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.spbstu.hsai.api.events.UpdateReceivedEvent;
import ru.spbstu.hsai.infrastructure.integration.telegram.TelegramSenderService;
import ru.spbstu.hsai.modules.simpletaskmanagment.model.SimpleTask;
import ru.spbstu.hsai.modules.simpletaskmanagment.service.SimpleTaskService;
import ru.spbstu.hsai.modules.usermanagement.service.UserService;
import org.telegram.telegrambots.meta.api.objects.Message;

@Component
public class MyTasksCommand implements TelegramCommand {
    private final TelegramSenderService sender;
    private final UserService userService;
    private final SimpleTaskService taskService;

    public MyTasksCommand(TelegramSenderService sender,
                          UserService userService,
                          SimpleTaskService taskService) {
        this.sender = sender;
        this.userService = userService;
        this.taskService = taskService;
    }

    @Override
    public boolean supports(String command) {
        return "/mytasks".equalsIgnoreCase(command);
    }

    @EventListener(condition = "@telegramCommandsExtract.checkCommand(#a0.update, '/mytasks')")
    public void handle(UpdateReceivedEvent event) {
        Message message = event.getUpdate().getMessage();
        User tgUser = message.getFrom();
        Long chatId = message.getChatId();

        userService.findByTelegramId(tgUser.getId())
                .flatMapMany(user -> taskService.getActiveTasks(user.getId()))
                .collectList()
                .subscribe(tasks -> {
                    if (tasks.isEmpty()) {
                        sender.sendAsync(new SendMessage(chatId.toString(), "⚡ У вас нет активных задач!\n\n"+
                                "Если хотите вернуться к списку команд, используйте /help"));
                    } else {
                        StringBuilder response = new StringBuilder("📋 Ваши активные задачи:\n\n");
                        int counter = 1;

                        for (SimpleTask task : tasks) {
                            response.append(counter++).append(". ")
                                    .append(task.toString()).append("\n\n");
                        }

                        response.append("Если хотите вернуться к списку команд, используйте /help");

                        SendMessage messageToSend = new SendMessage(chatId.toString(), response.toString());
                        messageToSend.enableHtml(true);
                        sender.sendAsync(messageToSend);
                    }
                }, error -> {
                    sender.sendAsync(new SendMessage(chatId.toString(),
                            "❌ Ошибка при получении задач: " + error.getMessage()));
                });
    }

}
