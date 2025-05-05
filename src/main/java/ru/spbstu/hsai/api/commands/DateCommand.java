package ru.spbstu.hsai.api.commands;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.spbstu.hsai.api.events.UpdateReceivedEvent;
import ru.spbstu.hsai.infrastructure.integration.telegram.TelegramSenderService;
import ru.spbstu.hsai.modules.simpletaskmanagment.service.SimpleTaskService;
import ru.spbstu.hsai.modules.usermanagement.service.UserService;
import ru.spbstu.hsai.modules.simpletaskmanagment.model.SimpleTask;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class DateCommand implements TelegramCommand{
    private final TelegramSenderService sender;
    private final UserService userService;
    private final SimpleTaskService taskService;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public DateCommand(TelegramSenderService sender,
                        UserService userService,
                        SimpleTaskService taskService) {
        this.sender = sender;
        this.userService = userService;
        this.taskService = taskService;
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

            if (date.isBefore(LocalDate.now())) {
                sender.sendAsync(new SendMessage(chatId.toString(),
                        "❌ Нельзя посмотреть задачи за прошедшие даты. Укажите текущую или будущую дату."));
                return;
            }

            userService.findByTelegramId(tgUser.getId())
                    .flatMapMany(user -> taskService.getTasksByDate(user.getId(), date))
                    .collectList()
                    .subscribe(tasks -> {
                        String formattedDate = date.format(dateFormatter);
                        if (tasks.isEmpty()) {
                            sender.sendAsync(new SendMessage(chatId.toString(),
                                    "⚡ У вас нет задач на " + formattedDate + "! Займитесь другими делами😀\n\n" +
                                            "Если хотите вернуться к списку команд, используйте /help"));
                        } else {
                            StringBuilder response = new StringBuilder("📋 Ваши задачи на " + formattedDate + ":\n\n");
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

        } catch (DateTimeParseException e) {
            sender.sendAsync(new SendMessage(chatId.toString(),
                    "❌ Неверный формат даты. Используйте дд.мм.гггг. Пример: /date 15.05.2025"));
        }
    }

}
