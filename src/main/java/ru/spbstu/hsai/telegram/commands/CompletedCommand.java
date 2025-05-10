package ru.spbstu.hsai.telegram.commands;


import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.spbstu.hsai.telegram.events.UpdateReceivedEvent;
import ru.spbstu.hsai.telegram.MessageSender;
import ru.spbstu.hsai.simpletaskmanagment.SimpleTask;
import ru.spbstu.hsai.simpletaskmanagment.SimpleTaskInterface;
import ru.spbstu.hsai.usermanagement.UserServiceInterface;

@Component
public class CompletedCommand implements TelegramCommand {
    private final MessageSender sender;
    private final UserServiceInterface userService;
    private final SimpleTaskInterface taskService;

    public CompletedCommand(MessageSender sender,
                            UserServiceInterface userService,
                            SimpleTaskInterface taskService) {
        this.sender = sender;
        this.userService = userService;
        this.taskService = taskService;
    }

    @Override
    public boolean supports(String command) {
        return "/completed".equalsIgnoreCase(command);
    }

    @EventListener(condition = "@telegramCommandsExtract.checkCommand(#a0.update, '/completed')")
    public void handle(UpdateReceivedEvent event) {
        Message message = event.getUpdate().getMessage();
        User tgUser = message.getFrom();
        Long chatId = message.getChatId();

        userService.findByTelegramId(tgUser.getId())
                .flatMapMany(user ->
                        taskService.getCompletedTasks(user.getId()))
                .collectList()
                .subscribe(tasks -> {
                    if (tasks.isEmpty()) {
                        sender.sendAsync(new SendMessage(chatId.toString(), "⚡️ У вас нет завершенных задач!\n\n"+
                                "Если хотите вернуться к списку команд, используйте /help"));
                    } else {
                        StringBuilder response = new StringBuilder("✅ Ваши завершенные задачи:\n\n");
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
