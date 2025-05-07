package ru.spbstu.hsai.api.commands;


import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.api.events.UpdateReceivedEvent;
import ru.spbstu.hsai.infrastructure.integration.telegram.TelegramSenderService;
import ru.spbstu.hsai.modules.repeatingtaskmanagment.model.RepeatingTask;
import ru.spbstu.hsai.modules.repeatingtaskmanagment.service.RepeatingTaskService;
import ru.spbstu.hsai.modules.simpletaskmanagment.model.SimpleTask;
import ru.spbstu.hsai.modules.simpletaskmanagment.service.SimpleTaskService;
import ru.spbstu.hsai.modules.usermanagement.service.UserService;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.List;

@Component
public class TodayCommand implements TelegramCommand{
    private final TelegramSenderService sender;
    private final UserService userService;
    private final SimpleTaskService taskService;
    private final RepeatingTaskService repeatingTaskService;

    public TodayCommand(TelegramSenderService sender,
                        UserService userService,
                        SimpleTaskService taskService,
                        RepeatingTaskService repeatingTaskService) {
        this.sender = sender;
        this.userService = userService;
        this.taskService = taskService;
        this.repeatingTaskService = repeatingTaskService;
    }

    @Override
    public boolean supports(String command) {
        return "/today".equalsIgnoreCase(command);
    }

    @EventListener(condition = "@telegramCommandsExtract.checkCommand(#a0.update, '/today')")
    public void handle(UpdateReceivedEvent event) {
        Message message = event.getUpdate().getMessage();
        User tgUser = message.getFrom();
        Long chatId = message.getChatId();

        userService.findByTelegramId(tgUser.getId())
                //.flatMapMany(user -> taskService.getTodayTasks(user.getId()))
                //.collectList()
                .flatMap(user -> {
                    Mono<List<SimpleTask>> simpleTasks = taskService.getTodayTasks(user.getId()).collectList();
                    Mono<List<RepeatingTask>> repeatingTasks = repeatingTaskService.getTodayTasks(user.getId()).collectList();

                    return Mono.zip(simpleTasks, repeatingTasks);
                })
                .subscribe(tuple -> {
                    List<SimpleTask> simpleTasks = tuple.getT1();
                    List<RepeatingTask> repeatingTasks = tuple.getT2();

                    if (simpleTasks.isEmpty() && repeatingTasks.isEmpty()) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "⚡ У вас нет задач на сегодня! Отдыхайте😌\n\n" +
                                        "Если хотите вернуться к списку команд, используйте /help"));
                        return;
                    }

                    StringBuilder sb = new StringBuilder();

                    // Вывод обычных задач
                    if (!simpleTasks.isEmpty()) {
                        sb.append("📋 Ваши задачи на сегодня:\n\n");
                        int counter = 1;
                        for (SimpleTask task : simpleTasks) {
                            sb.append(counter++).append(". ")
                                    .append(task.toString()).append("\n\n");
                        }
                    }

                    // Вывод периодических задач
                    if (!repeatingTasks.isEmpty()) {
                        if (!simpleTasks.isEmpty()) {
                            sb.append("\n");
                        }
                        sb.append("🔁 Периодические задачи:\n\n");
                        int counter = 1;
                        for (RepeatingTask task : repeatingTasks) {
                            sb.append(counter++).append(". ")
                                    .append(task.toString()).append("\n\n");
                        }
                    }

                    sb.append("\nЕсли хотите вернуться к списку команд, используйте /help");

                    SendMessage messageToSend = new SendMessage(chatId.toString(), sb.toString());
                    messageToSend.enableHtml(true);
                    sender.sendAsync(messageToSend);
                }, error -> {
                    sender.sendAsync(new SendMessage(chatId.toString(),
                            "❌ Ошибка при получении задач: " + error.getMessage()));
                });







                /*
                .subscribe(tasks -> {
                    if (tasks.isEmpty()) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "⚡ У вас нет задач на сегодня! Отдыхайте😌\n\n" +
                                        "Если хотите вернуться к списку команд, используйте /help"));
                    } else {
                        StringBuilder response = new StringBuilder("📋 Ваши задачи на сегодня:\n\n");
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
                }); */
    }

}
