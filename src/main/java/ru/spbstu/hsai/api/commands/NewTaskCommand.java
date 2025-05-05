package ru.spbstu.hsai.api.commands;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.spbstu.hsai.api.context.simpleTaskCreation.SimpleTaskCreationContext;
import ru.spbstu.hsai.api.context.simpleTaskCreation.SimpleTaskCreationState;
import ru.spbstu.hsai.api.context.simpleTaskCreation.SimpleTaskCreationStep;
import ru.spbstu.hsai.api.events.UpdateReceivedEvent;
import ru.spbstu.hsai.infrastructure.integration.telegram.TelegramSenderService;
import ru.spbstu.hsai.modules.simpletaskmanagment.model.SimpleTask;
import ru.spbstu.hsai.modules.simpletaskmanagment.service.SimpleTaskService;
import ru.spbstu.hsai.modules.usermanagement.service.UserService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class NewTaskCommand implements TelegramCommand {
    private final TelegramSenderService sender;
    private final UserService userService;
    private final SimpleTaskService taskService;
    private final SimpleTaskCreationContext creationContext;

    public NewTaskCommand(TelegramSenderService sender,
                          UserService userService,
                          SimpleTaskService taskService,
                          SimpleTaskCreationContext creationContext) {
        this.sender = sender;
        this.userService = userService;
        this.taskService = taskService;
        this.creationContext = creationContext;
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

        System.out.println("Received /newtask command from chat " + chatId);

        if (!creationContext.hasActiveSession(chatId)) {
            userService.findByTelegramId(tgUser.getId())
                    .subscribe(user -> {
                        System.out.println("Starting task creation for user " + user.getId());
                        creationContext.startCreation(chatId, user.getId());
                        askForDescription(chatId);
                    }, error -> {
                        System.err.println("Error finding user: " + error.getMessage());
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Ошибка при поиске пользователя"));
                    });
        } else {
            processUserInput(chatId, message.getText());
        }
    }

    @EventListener
    public void handleRegularMessage(UpdateReceivedEvent event) {
        Message message = event.getUpdate().getMessage();
        if (message == null || !message.hasText() || message.isCommand()) {
            return;
        }

        Long chatId = message.getChatId();
        if (creationContext.hasActiveSession(chatId)) {
            System.out.println("Processing regular message for chat " + chatId);
            processUserInput(chatId, message.getText());
        }
    }

    private void processUserInput(Long chatId, String input) {
        SimpleTaskCreationState state = creationContext.getState(chatId);

        try {
            switch (state.getCurrentStep()) {
                case DESCRIPTION:
                    if (input.length() > 200) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Описание задачи не должно превышать 200 символов. Повторите ввод."));
                        return;
                    }
                    state.setDescription(input);
                    state.setCurrentStep(SimpleTaskCreationStep.COMPLEXITY);
                    askForComplexity(chatId);
                    break;

                case COMPLEXITY:
                    int complexity = Integer.parseInt(input);
                    if (complexity < 1 || complexity > 5) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Оцените сложность задачи, введя целое число в диапазоне от 1 до 5. " +
                                        "Повторите ввод."));
                        return;
                    }
                    state.setComplexity(complexity);
                    state.setCurrentStep(SimpleTaskCreationStep.DEADLINE);
                    askForDeadline(chatId);
                    break;

                case DEADLINE:
                    LocalDate deadline = parseDate(input);
                    if (deadline == null || deadline.isBefore(LocalDate.now())) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Укажите дедлайн задачи в формате дд.мм.гггг не ранее текущей даты. " +
                                        "Повторите ввод."));
                        return;
                    }
                    state.setDeadline(deadline);
                    state.setCurrentStep(SimpleTaskCreationStep.REMINDER);
                    askForReminder(chatId);
                    break;

                case REMINDER:
                    int reminderChoice = Integer.parseInt(input);
                    if (reminderChoice < 1 || reminderChoice > 4) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Введите целое число в диапазоне от 1 до 4 в зависимости от " +
                                        "выбранного действия. Повторите ввод."));
                        return;
                    }

                    SimpleTask.ReminderType reminder = convertToReminderType(reminderChoice);

                    // Проверяем валидность напоминания
                    if (reminder != SimpleTask.ReminderType.NO_REMINDER &&
                            !isReminderValid(state.getDeadline(), reminder)) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Нельзя установить напоминание на прошедшую дату. " +
                                        "Пожалуйста, выберите другое напоминание."));
                        return;
                    }

                    state.setReminder(reminder);
                    completeTaskCreation(chatId, state);
                    break;
            }
            creationContext.updateState(chatId, state);
        } catch (NumberFormatException e) {
            sender.sendAsync(new SendMessage(chatId.toString(),
                    "❌ Пожалуйста, введите число."));
        }
    }


    private void askForDescription(Long chatId) {
        sender.sendAsync(new SendMessage(chatId.toString(), "📌 Введите описание задачи:"));
    }

    private void askForComplexity(Long chatId) {
        sender.sendAsync(new SendMessage(chatId.toString(),
                "📊 Оцените сложность задачи от 1 до 5:"));
    }

    private void askForDeadline(Long chatId) {
        sender.sendAsync(new SendMessage(chatId.toString(),
                "🗓️ Укажите дедлайн (в формате дд.мм.гггг):"));
    }

    private void askForReminder(Long chatId) {
        String message = "⏰ Установить напоминание о задаче до дедлайна?\n" +
                "1. За 1 час\n" +
                "2. За 1 день\n" +
                "3. За 1 неделю\n" +
                "4. Без напоминания";
        sender.sendAsync(new SendMessage(chatId.toString(), message));
    }

    private void completeTaskCreation(Long chatId, SimpleTaskCreationState state) {
        taskService.createTask(
                state.getUserId(),
                state.getDescription(),
                state.getComplexity(),
                state.getDeadline(),
                state.getReminder()
        ).subscribe(
                task -> {
                    String successMessage = "✅Задача создана!\n" + task.toString() +
                            "\nЕсли хотите вернуться к списку команд, используйте /help";;
                    SendMessage response = new SendMessage(chatId.toString(), successMessage);
                    response.enableHtml(true);
                    sender.sendAsync(response);
                    creationContext.complete(chatId);
                },
                error -> {
                    sender.sendAsync(new SendMessage(chatId.toString(),
                            "❌ Ошибка при создании задачи: " + error.getMessage()));
                    creationContext.complete(chatId);
                }
        );
    }

    private LocalDate parseDate(String input) {
        try {
            return LocalDate.parse(input, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private SimpleTask.ReminderType convertToReminderType(int choice) {
        return switch (choice) {
            case 1 -> SimpleTask.ReminderType.ONE_HOUR_BEFORE;
            case 2 -> SimpleTask.ReminderType.ONE_DAY_BEFORE;
            case 3 -> SimpleTask.ReminderType.ONE_WEEK_BEFORE;
            default -> SimpleTask.ReminderType.NO_REMINDER;
        };
    }

    private boolean isReminderValid(LocalDate deadline, SimpleTask.ReminderType reminder) {
        LocalDate reminderDate = calculateReminderDate(deadline, reminder);
        return reminderDate.isAfter(LocalDate.now());
    }

    private LocalDate calculateReminderDate(LocalDate deadline, SimpleTask.ReminderType reminder) {
        return switch (reminder) {
            case ONE_HOUR_BEFORE -> deadline; // Для часового напоминания проверяем сам дедлайн
            case ONE_DAY_BEFORE -> deadline.minusDays(1);
            case ONE_WEEK_BEFORE -> deadline.minusWeeks(1);
            case NO_REMINDER -> deadline; // Для "без напоминания" проверка не нужна
        };
    }

}
