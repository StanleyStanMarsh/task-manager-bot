package ru.spbstu.hsai.api.commands;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.api.commands.utils.FormattedRepeatingTask;
import ru.spbstu.hsai.api.events.UpdateReceivedEvent;
import ru.spbstu.hsai.infrastructure.integration.telegram.TelegramSenderService;
import ru.spbstu.hsai.modules.repeatingtaskmanagment.model.RepeatingTask;
import ru.spbstu.hsai.modules.repeatingtaskmanagment.service.RepeatingTaskService;
import ru.spbstu.hsai.modules.simpletaskmanagment.service.SimpleTaskService;
import ru.spbstu.hsai.modules.usermanagement.service.UserService;
import ru.spbstu.hsai.modules.simpletaskmanagment.model.SimpleTask;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

@Component
public class DateCommand implements TelegramCommand{
    private final TelegramSenderService sender;
    private final UserService userService;
    private final SimpleTaskService taskService;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final RepeatingTaskService repeatingTaskService;

    public DateCommand(TelegramSenderService sender,
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
        return "/date".equalsIgnoreCase(command);
    }

    @EventListener(condition = "@telegramCommandsExtract.checkCommand(#a0.update, '/date')")
    public void handle(UpdateReceivedEvent event) {
        Message message = event.getUpdate().getMessage();
        User tgUser = message.getFrom();
        Long chatId = message.getChatId();
        String text = message.getText();

        // Извлекаем дату из команды
        String dateString = text.substring("/date".length()).trim();

        if (dateString.isEmpty()) {
            sender.sendAsync(new SendMessage(chatId.toString(),
                    "❌ Укажите дату в формате дд.мм.гггг. Пример: /date 15.05.2025"));
            return;
        }

        try {
            LocalDate date = LocalDate.parse(dateString, dateFormatter);

            userService.findByTelegramId(tgUser.getId())
                    .flatMap(user -> {
                        ZoneId zoneId = ZoneId.of(user.getTimezone()); // <-- получаем часовой пояс
                        LocalDate todayInZone = LocalDate.now(zoneId);

                        if (date.isBefore(todayInZone)) {
                            sender.sendAsync(new SendMessage(chatId.toString(),
                                    "❌ Нельзя посмотреть задачи за прошедшие даты. Укажите текущую или будущую дату."));
                            return Mono.empty();
                        }
                        Mono<List<SimpleTask>> simpleTasks = taskService
                                .getTasksByDate(user.getId(), date).collectList();
                        Mono<List<RepeatingTask>> repeatingTasks = repeatingTaskService
                                .getTasksByDate(user.getId(), date, zoneId).collectList();
                        Mono<String> timezone = Mono.just(user.getTimezone());

                        return Mono.zip(simpleTasks, repeatingTasks, timezone);
                    })
                    .subscribe(tuple -> {
                        List<SimpleTask> simpleTasks = tuple.getT1();
                        List<RepeatingTask> repeatingTasks = tuple.getT2();
                        String timezone = tuple.getT3();

                        String formattedDate = date.format(dateFormatter);

                        if (simpleTasks.isEmpty() && repeatingTasks.isEmpty()) {
                            sender.sendAsync(new SendMessage(chatId.toString(),
                                    "⚡ У вас нет задач на " + formattedDate + "! Займитесь другими делами😀\n\n" +
                                            "Если хотите вернуться к списку команд, используйте /help"));
                            return;
                        }

                        StringBuilder sb = new StringBuilder();

                        // Вывод обычных задач
                        if (!simpleTasks.isEmpty()) {
                            sb.append("📋 Ваши задачи на " + formattedDate + ":\n\n");
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
                            repeatingTasks.sort(Comparator.comparing(RepeatingTask::getNextExecution));
                            int counter = 1;
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

                            for (RepeatingTask task : repeatingTasks) {
                                sb.append(counter++).append(". ")
                                        .append(FormattedRepeatingTask.format(task, ZoneId.of(timezone)))
                                        .append("\n\n");
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

        } catch (DateTimeParseException e) {
            sender.sendAsync(new SendMessage(chatId.toString(),
                    "❌ Неверный формат даты. Используйте дд.мм.гггг. Пример: /date 15.05.2025"));
        }
    }

}
