package ru.spbstu.hsai.telegram.commands;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.spbstu.hsai.telegram.commands.utils.FormattedRepeatingTask;
import ru.spbstu.hsai.telegram.commands.utils.TaskValidation;
import ru.spbstu.hsai.telegram.context.RepeatingTaskCreation.RepeatingTaskCreationContext;
import ru.spbstu.hsai.telegram.context.RepeatingTaskCreation.RepeatingTaskCreationState;
import ru.spbstu.hsai.telegram.context.RepeatingTaskCreation.RepeatingTaskCreationStep;
import ru.spbstu.hsai.telegram.events.UpdateReceivedEvent;
import ru.spbstu.hsai.telegram.MessageSender;
import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTask;
import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTaskInterface;
import ru.spbstu.hsai.usermanagement.UserServiceInterface;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class NewRepeatingTaskCommand implements TelegramCommand{
    private final MessageSender sender;
    private final UserServiceInterface userService;
    private final RepeatingTaskInterface taskService;
    private final RepeatingTaskCreationContext creationContext;

    public NewRepeatingTaskCommand(MessageSender sender,
                                   UserServiceInterface userService,
                                   RepeatingTaskInterface taskService,
                                   RepeatingTaskCreationContext creationContext) {
        this.sender = sender;
        this.userService = userService;
        this.taskService = taskService;
        this.creationContext = creationContext;
    }

    @Override
    public boolean supports(String command) {
        return "/newrepeatingtask".equalsIgnoreCase(command);
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

    @EventListener(condition = "@telegramCommandsExtract.checkCommand(#a0.update, '/newrepeatingtask')")
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


    private void processUserInput(Long chatId, String input) {
        RepeatingTaskCreationState state = creationContext.getState(chatId);

        try {
            switch (state.getCurrentStep()) {
                case DESCRIPTION:
                    if (input.length() > 200) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Описание задачи не должно превышать 200 символов. Повторите ввод."));
                        return;
                    }
                    state.setDescription(input);
                    state.setCurrentStep(RepeatingTaskCreationStep.COMPLEXITY);
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
                    state.setCurrentStep(RepeatingTaskCreationStep.FREQUENCY);
                    askForFrequency(chatId);
                    break;

                case FREQUENCY:
                    int frequencyChoice = Integer.parseInt(input);
                    if (frequencyChoice < 1 || frequencyChoice > 4) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Введите целое число в диапазоне от 1 до 4 в зависимости от " +
                                        "выбранного действия. Повторите ввод."));
                        return;
                    }

                    RepeatingTask.RepeatFrequency frequency = TaskValidation.convertToFrequencyType(frequencyChoice);


                    state.setFrequency(frequency);
                    state.setCurrentStep(RepeatingTaskCreationStep.START_DATE);
                    askForStartDate(chatId);
                    break;

                case START_DATE:
                    LocalDateTime userInputDateTime = TaskValidation.parseDateTime(input);
                    if (userInputDateTime == null) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Укажите дату и время в формате дд.мм.гггг чч:мм.\n" +
                                        "Повторите ввод."));
                        return;
                    }

                    userService.findByTelegramId(chatId).subscribe(user -> {
                        ZoneId userZone = ZoneId.of(user.getTimezone());
                        LocalDateTime currentUserTime = LocalDateTime.now(userZone);

                        ZonedDateTime userZonedDateTime = userInputDateTime.atZone(userZone);
                        ZonedDateTime moscowZonedDateTime = userZonedDateTime.withZoneSameInstant(ZoneId.of("Europe/Moscow"));
                        LocalDateTime moscowTime = moscowZonedDateTime.toLocalDateTime();

                        if (userInputDateTime.isBefore(currentUserTime)) {
                            sender.sendAsync(new SendMessage(chatId.toString(),
                                    "❌ Укажите корректную дату и время в формате дд.мм.гггг чч:мм, " +
                                            "не ранее текущего момента.\n" +
                                            "Повторите ввод.\n" +
                                            "Текущее время по вашему часовому поясу: " + currentUserTime.format(DateTimeFormatter.ofPattern("HH:mm"))));
                            return;
                        }

                        state.setStartDateTime(moscowTime);
                        creationContext.updateState(chatId, state);
                        completeTaskCreation(chatId, state, userZone);
                    }, error -> {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Не удалось получить ваш часовой пояс. Повторите позже."));
                    });
                    return;

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

    private void askForFrequency(Long chatId) {
        String message = "🔁 Укажите период повторения задачи:\n" +
                "1. Ежечасно\n" +
                "2. Ежедневно\n" +
                "3. Еженедельно\n" +
                "4. Ежемесячно";
        sender.sendAsync(new SendMessage(chatId.toString(), message));
    }

    private void askForStartDate(Long chatId) {
        sender.sendAsync(new SendMessage(chatId.toString(),
                "🗓️ Укажите дату и время начала задачи (в формате дд.мм.гггг чч:мм) "));
    }

    private void completeTaskCreation(Long chatId, RepeatingTaskCreationState state, ZoneId userZoneId) {
        taskService.createTask(
                state.getUserId(),
                state.getDescription(),
                state.getComplexity(),
                state.getFrequency(),
                state.getStartDateTime()
        ).subscribe(
                task -> {
//                    String successMessage = "✅Задача создана!\n" + task.toString() +
//                            "\n\nЕсли хотите вернуться к списку команд, используйте /help";
                    // выводим корректный пояс
                    FormattedRepeatingTask ft = new FormattedRepeatingTask(task);

                    String successMessage = ft.format(userZoneId);

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
