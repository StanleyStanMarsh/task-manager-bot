package ru.spbstu.hsai.api.commands;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.api.commands.utils.FormattedRepeatingTask;
import ru.spbstu.hsai.api.commands.utils.FormattedSimpleTask;
import ru.spbstu.hsai.api.commands.utils.StringSplitter;
import ru.spbstu.hsai.api.UpdateReceivedEvent;
import ru.spbstu.hsai.core.MessageSender;
import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTask;
import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTaskInterface;
import ru.spbstu.hsai.simpletaskmanagment.SimpleTask;
import ru.spbstu.hsai.simpletaskmanagment.SimpleTaskInterface;
import ru.spbstu.hsai.usermanagement.UserServiceInterface;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Component
public class MyTasksCommand implements TelegramCommand {
    private final MessageSender sender;
    private final UserServiceInterface userService;
    private final SimpleTaskInterface taskService;
    private final RepeatingTaskInterface repeatingTaskService;

    public MyTasksCommand(MessageSender sender,
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
        return "/mytasks".equalsIgnoreCase(command);
    }

    @EventListener(condition = "@telegramCommandsExtract.checkCommand(#a0.update, '/mytasks')")
    public void handle(UpdateReceivedEvent event) {
        Message message = event.getUpdate().getMessage();
        User tgUser = message.getFrom();
        Long chatId = message.getChatId();

        userService.findByTelegramId(tgUser.getId())
                .flatMap(user -> {
                    Mono<List<SimpleTask>> simpleTasks = taskService.getActiveTasks(user.getId()).collectList();
                    Mono<List<RepeatingTask>> repeatingTasks = repeatingTaskService.getActiveTasks(user.getId()).collectList();

                    return Mono.zip(simpleTasks, repeatingTasks, Mono.just(user.getTimezone()));
                })
                .subscribe(tuple -> {
                    List<SimpleTask> simpleTasks = tuple.getT1();
                    List<RepeatingTask> repeatingTasks = tuple.getT2();
                    String stringUserTimezone = tuple.getT3();

                    if (simpleTasks.isEmpty() && repeatingTasks.isEmpty()) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "⚡ У вас нет активных задач!\n\n" +
                                        "Если хотите вернуться к списку команд, используйте /help"));
                        return;
                    }

                    StringBuilder sb = new StringBuilder();

                    // Вывод обычных задач
                    if (!simpleTasks.isEmpty()) {
                        sb.append("📋 Ваши активные задачи:\n\n");
                        // Сортируем задачи по дате дедлайна
                        simpleTasks.sort(Comparator.comparing(SimpleTask::getDeadline));
                        int counter = 1;
                        for (SimpleTask task : simpleTasks) {
                            FormattedSimpleTask ft = new FormattedSimpleTask(task);
                            sb.append(counter++).append(". ")
                                    .append(ft.format(ZoneId.of(stringUserTimezone)))
                                    .append("\n\n");
                        }
                    }

                    // Вывод периодических задач
                    if (!repeatingTasks.isEmpty()) {
                        if (!simpleTasks.isEmpty()) {
                            sb.append("\n");
                        }
                        sb.append("🔁 Периодические задачи:\n\n");
                        repeatingTasks.sort(Comparator.comparing(RepeatingTask::getNextExecution));
                        int counter = 1;
                        for (RepeatingTask task : repeatingTasks) {
                            FormattedRepeatingTask ft = new FormattedRepeatingTask(task);
                            sb.append(counter++).append(". ")
                                    .append(ft.format(ZoneId.of(stringUserTimezone)))
                                    .append("\n\n");
                        }
                    }

                    sb.append("\nЕсли хотите вернуться к списку команд, используйте /help");

                    // Разбиваем на чанки по 4096 символов
                    List<String> parts = StringSplitter.splitToChunks(sb.toString(), 4000);

                    // Отправляем последовательно
                    Flux.fromIterable(parts)
                            .concatMap(part -> {
                                SendMessage msg = SendMessage.builder()
                                        .chatId(chatId.toString())
                                        .text(part)
                                        .build();
                                msg.enableHtml(true);
                                return sender.sendReactive(msg);
                            })
                            .subscribe();
                }, error -> {
                    sender.sendAsync(new SendMessage(chatId.toString(),
                            "❌ Ошибка при получении задач: " + error.getMessage()));
                });
    }

}
