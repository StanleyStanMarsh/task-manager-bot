package ru.spbstu.hsai.telegram.commands;


import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.telegram.commands.utils.FormattedRepeatingTask;
import ru.spbstu.hsai.telegram.commands.utils.FormattedSimpleTask;
import ru.spbstu.hsai.telegram.commands.utils.StringSplitter;
import ru.spbstu.hsai.telegram.events.UpdateReceivedEvent;
import ru.spbstu.hsai.telegram.MessageSender;
import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTask;
import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTaskInterface;
import ru.spbstu.hsai.simpletaskmanagment.SimpleTask;
import ru.spbstu.hsai.simpletaskmanagment.SimpleTaskInterface;
import ru.spbstu.hsai.usermanagement.UserServiceInterface;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Component
public class TodayCommand implements TelegramCommand{
    private final MessageSender sender;
    private final UserServiceInterface userService;
    private final SimpleTaskInterface taskService;
    private final RepeatingTaskInterface repeatingTaskService;

    public TodayCommand(MessageSender sender,
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
        return "/today".equalsIgnoreCase(command);
    }

    @EventListener(condition = "@telegramCommandsExtract.checkCommand(#a0.update, '/today')")
    public void handle(UpdateReceivedEvent event) {
        Message message = event.getUpdate().getMessage();
        User tgUser = message.getFrom();
        Long chatId = message.getChatId();

        userService.findByTelegramId(tgUser.getId())
                .flatMap(user -> {
                    ZoneId zoneId = ZoneId.of(user.getTimezone()); // поле ZoneId должно быть в User
                    Mono<List<SimpleTask>> simpleTasks = taskService.getTodayTasks(user.getId(), zoneId).collectList();
                    Mono<List<RepeatingTask>> repeatingTasks = repeatingTaskService.getTodayTasks(user.getId(), zoneId).collectList();
                    Mono<String> timezone = Mono.just(user.getTimezone());
                    return Mono.zip(simpleTasks, repeatingTasks, timezone);
                })
                .subscribe(tuple -> {
                    List<SimpleTask> simpleTasks = tuple.getT1();
                    List<RepeatingTask> repeatingTasks = tuple.getT2();
                    String timezone = tuple.getT3();

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
                            FormattedSimpleTask ft = new FormattedSimpleTask(task);
                            sb.append(counter++).append(". ")
                                    .append(ft.format(ZoneId.of(timezone)))
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
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

                        for (RepeatingTask task : repeatingTasks) {
                            FormattedRepeatingTask ft = new FormattedRepeatingTask(task);
                            sb.append(counter++).append(". ")
                                    .append(ft.format(ZoneId.of(timezone)))
                                    .append("\n\n");
                        }
                    }

                    sb.append("\nЕсли хотите вернуться к списку команд, используйте /help");

                    // Разбиваем на чанки по 4096 символов
                    List<String> parts = StringSplitter.splitToChunks(sb.toString(), 4000);

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
