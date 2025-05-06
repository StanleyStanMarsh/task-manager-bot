package ru.spbstu.hsai.api.commands;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.spbstu.hsai.api.commands.utils.TaskValidation;
import ru.spbstu.hsai.api.context.simpleTaskCreation.SimpleTaskCreationContext;
import ru.spbstu.hsai.api.context.simpleTaskCreation.SimpleTaskCreationState;
import ru.spbstu.hsai.api.context.simpleTaskCreation.SimpleTaskCreationStep;
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
                    LocalDate deadline = TaskValidation.parseDate(input);
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

                    SimpleTask.ReminderType reminder = TaskValidation.convertToReminderType(reminderChoice);

                    // Проверяем валидность напоминания
                    if (reminder != SimpleTask.ReminderType.NO_REMINDER &&
                            !TaskValidation.isReminderValid(state.getDeadline(), reminder)) {
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
                            "\n\nЕсли хотите вернуться к списку команд, используйте /help";
                    SendMessage response = new SendMessage(chatId.toString(), successMessage);
                    response.enableHtml(true);
                    sender.sendAsync(response);
                    creationContext.complete(chatId);
                },
                error -> {
                    String errorMessage;
                    if (error.getMessage() != null && error.getMessage().startsWith("EXISTING_TASK:")) {
                        String taskInfo = error.getMessage().substring("EXISTING_TASK:".length());
                        errorMessage = "❌ Эта задача уже существует в вашем списке:\n\n" + taskInfo +
                                "\n\nЕсли хотите вернуться к списку команд, используйте /help";;
                    } else {
                        errorMessage = "❌ Ошибка при создании задачи: " + error.getMessage();
                    }

                    SendMessage response = new SendMessage(chatId.toString(), errorMessage);
                    response.enableHtml(true);
                    sender.sendAsync(response);
                    creationContext.complete(chatId);
                }
        );
    }

}
