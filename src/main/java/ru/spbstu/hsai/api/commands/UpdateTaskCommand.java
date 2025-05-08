package ru.spbstu.hsai.api.commands;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.api.commands.utils.TaskValidation;
import ru.spbstu.hsai.api.context.repeatingTaskUpdate.RepeatingTaskUpdateContext;
import ru.spbstu.hsai.api.context.repeatingTaskUpdate.RepeatingTaskUpdateState;
import ru.spbstu.hsai.api.context.repeatingTaskUpdate.RepeatingTaskUpdateStep;
import ru.spbstu.hsai.api.context.simpleTaskUpdate.SimpleTaskUpdateContext;
import ru.spbstu.hsai.api.context.simpleTaskUpdate.SimpleTaskUpdateState;
import ru.spbstu.hsai.api.context.simpleTaskUpdate.SimpleTaskUpdateStep;
import ru.spbstu.hsai.api.events.UpdateReceivedEvent;
import ru.spbstu.hsai.infrastructure.integration.telegram.TelegramSenderService;
import ru.spbstu.hsai.modules.repeatingtaskmanagment.model.RepeatingTask;
import ru.spbstu.hsai.modules.repeatingtaskmanagment.service.RepeatingTaskService;
import ru.spbstu.hsai.modules.simpletaskmanagment.model.SimpleTask;
import ru.spbstu.hsai.modules.simpletaskmanagment.service.SimpleTaskService;
import ru.spbstu.hsai.modules.usermanagement.service.UserService;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class UpdateTaskCommand implements TelegramCommand {
    private final TelegramSenderService sender;
    private final UserService userService;
    private final SimpleTaskService taskService;
    private final SimpleTaskUpdateContext updateContext;
    private final RepeatingTaskService repeatingTaskService;
    private final RepeatingTaskUpdateContext repeatingTaskUpdateContext;

    public UpdateTaskCommand(TelegramSenderService sender,
                             UserService userService,
                             SimpleTaskService taskService,
                             SimpleTaskUpdateContext updateContext,
                             RepeatingTaskService repeatingTaskService,
                             RepeatingTaskUpdateContext repeatingTaskUpdateContext) {
        this.sender = sender;
        this.userService = userService;
        this.taskService = taskService;
        this.updateContext = updateContext;
        this.repeatingTaskService = repeatingTaskService;
        this.repeatingTaskUpdateContext = repeatingTaskUpdateContext;
    }

    @Override
    public boolean supports(String command) {
        return "/updatetask".equalsIgnoreCase(command);
    }

    @EventListener
    public void handleRegularMessage(UpdateReceivedEvent event) {
        Message message = event.getUpdate().getMessage();
        if (message == null || !message.hasText() || message.isCommand()) {
            return;
        }

        Long chatId = message.getChatId();
        if (updateContext.hasActiveSession(chatId)) {
            processUserInput(chatId, message.getText());
        } else if (repeatingTaskUpdateContext.hasActiveSession(chatId)) {
            processUserInputForRepeatingTasks(chatId, message.getText());
        }
    }

    @EventListener(condition = "@telegramCommandsExtract.checkCommand(#a0.update, '/updatetask')")
    public void handle(UpdateReceivedEvent event) {
        Message message = event.getUpdate().getMessage();
        User tgUser = message.getFrom();
        Long chatId = message.getChatId();
        String text = message.getText();

        // Извлекаем ID из команды
        String taskId = text.substring("/updatetask".length()).trim();

        if (taskId.isEmpty()) {
            sender.sendAsync(new SendMessage(chatId.toString(),
                    "❌ Укажите ID задачи. Пример: /updatetask 68189edc1c9de42dfbcc7c79"));
            return;
        }

        userService.findByTelegramId(tgUser.getId())
                .flatMap(user -> {
                    // Проверяем сначала SimpleTask
                    return taskService.taskExistsAndBelongsToUser(taskId, user.getId())
                            .flatMap(isSimpleTask -> {
                                if (isSimpleTask) {
                                    updateContext.startUpdate(chatId, taskId, user.getId());
                                    askWhatToUpdate(chatId);
                                    return Mono.just(true);
                                }
                                // Если не SimpleTask, проверяем RepeatingTask
                                return repeatingTaskService.taskExistsAndBelongsToUser(taskId, user.getId())
                                        .doOnNext(isRepeatingTask -> {
                                            if (isRepeatingTask) {
                                                repeatingTaskUpdateContext.startUpdate(chatId, taskId, user.getId());
                                                askWhatToUpdateForRepeatingTask(chatId);
                                            } else {
                                                sender.sendAsync(new SendMessage(chatId.toString(),
                                                        "❌ Задача с ID " + taskId + " не найдена или не принадлежит вам.\n" +
                                                                "Проверьте корректность ввода и попробуйте снова."));
                                            }
                                        });
                            });
                })
                .subscribe();

    }

    private void askWhatToUpdate(Long chatId) {
        String message = "🤔 Что вы хотите изменить?\n" +
                "1. Описание\n" +
                "2. Сложность\n" +
                "3. Дедлайн\n" +
                "4. Напоминание\n" +
                "5. Отметить задачу как выполненную\n" +
                "6. Отмена";

        SimpleTaskUpdateState state = updateContext.getState(chatId);
        state.setCurrentStep(SimpleTaskUpdateStep.SELECT_FIELD);
        updateContext.updateState(chatId, state);

        sender.sendAsync(new SendMessage(chatId.toString(), message));

    }

    private void askWhatToUpdateForRepeatingTask(Long chatId) {
        String message = "🤔 Что вы хотите изменить?\n" +
                "1. Описание\n" +
                "2. Сложность\n" +
                "3. Периодичность\n" +
                "4. Дату и время начала задачи\n" +
                "5. Отмена";

        RepeatingTaskUpdateState state = repeatingTaskUpdateContext.getState(chatId);
        state.setCurrentStep(RepeatingTaskUpdateStep.SELECT_FIELD);
        repeatingTaskUpdateContext.updateState(chatId, state);

        sender.sendAsync(new SendMessage(chatId.toString(), message));

    }

    private void processUserInput(Long chatId, String input) {
        SimpleTaskUpdateState state = updateContext.getState(chatId);

        try {
            switch (state.getCurrentStep()) {
                case SELECT_FIELD:
                    int choice = Integer.parseInt(input);
                    if (choice < 1 || choice > 6) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Введите целое число в диапазоне от 1 до 6 в " +
                                        "зависимости от выбранного действия. Повторите ввод."));
                        return;
                    }

                    switch (choice) {
                        case 1:
                            state.setCurrentStep(SimpleTaskUpdateStep.NEW_DESCRIPTION);
                            askForNewDescription(chatId);
                            break;
                        case 2:
                            state.setCurrentStep(SimpleTaskUpdateStep.NEW_COMPLEXITY);
                            askForNewComplexity(chatId);
                            break;
                        case 3:
                            state.setCurrentStep(SimpleTaskUpdateStep.NEW_DEADLINE);
                            askForNewDeadline(chatId);
                            break;
                        case 4:
                            state.setCurrentStep(SimpleTaskUpdateStep.NEW_REMINDER);
                            askForNewReminder(chatId);
                            break;
                        case 5:
                            completeTaskUpdate(chatId, state, true);
                            break;
                        case 6:
                            cancelTaskUpdate(chatId);
                            break;
                    }
                    break;

                case NEW_DESCRIPTION:
                    if (input.length() > 200) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Описание задачи не должно превышать 200 символов. Повторите ввод."));
                        return;
                    }
                    state.setDescription(input);
                    completeTaskUpdate(chatId, state, false);
                    break;

                case NEW_COMPLEXITY:
                    int complexity = Integer.parseInt(input);
                    if (complexity < 1 || complexity > 5) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Оцените сложность задачи, введя целое число в диапазоне от 1 до 5. Повторите ввод."));
                        return;
                    }
                    state.setComplexity(complexity);
                    completeTaskUpdate(chatId, state, false);
                    break;

                case NEW_DEADLINE:
                    LocalDate deadline = TaskValidation.parseDate(input);
                    if (deadline == null || deadline.isBefore(LocalDate.now())) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Укажите дедлайн задачи в формате дд.мм.гггг не ранее текущей даты. " +
                                        "Повторите ввод."));
                        return;
                    }
                    state.setDeadline(deadline);
                    completeTaskUpdate(chatId, state, false);
                    break;

                case NEW_REMINDER:
                    int reminderChoice = Integer.parseInt(input);
                    if (reminderChoice < 1 || reminderChoice > 4) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Введите целое число в диапазоне от 1 до 4 в зависимости от " +
                                        "выбранного действия. Повторите ввод."));
                        return;
                    }
                    SimpleTask.ReminderType reminder = TaskValidation.convertToReminderType(reminderChoice);

                    // Проверяем валидность напоминания
                    // Получаем дедлайн (либо из состояния, либо из БД)
                    Mono<LocalDate> deadlineMono = (state.getDeadline() != null)
                            ? Mono.just(state.getDeadline())
                            : taskService.findTaskByIdAndUser(state.getTaskId(), state.getUserId())
                            .map(SimpleTask::getDeadline);

                    deadlineMono.subscribe(taskdeadline -> {
                                if (reminder != SimpleTask.ReminderType.NO_REMINDER) {
                                    if (!TaskValidation.isReminderValid(taskdeadline, reminder)) {
                                        sender.sendAsync(new SendMessage(chatId.toString(),
                                                "❌ Нельзя установить напоминание на прошедшую дату. " +
                                                        "Пожалуйста, выберите другое напоминание."));
                                        return;
                                    }
                                }
                        state.setReminder(reminder);
                        completeTaskUpdate(chatId, state, false);
                    });
                    break;
            }
            updateContext.updateState(chatId, state);
        } catch (NumberFormatException e) {
            sender.sendAsync(new SendMessage(chatId.toString(),
                    "❌ Пожалуйста, введите число."));
        }
    }

    private void askForNewDescription(Long chatId) {
        sender.sendAsync(new SendMessage(chatId.toString(), "📌 Введите новое описание задачи:"));
    }

    private void askForNewComplexity(Long chatId) {
        sender.sendAsync(new SendMessage(chatId.toString(), "📊 Оцените сложность задачи от 1 до 5:"));
    }

    private void askForNewDeadline(Long chatId) {
        sender.sendAsync(new SendMessage(chatId.toString(), "🗓️ Укажите новый дедлайн (в формате дд.мм.гггг):"));
    }

    private void askForNewReminder(Long chatId) {
        String message = "⏰ Установить новое напоминание о задаче до дедлайна?\n" +
                "1. За 1 час\n" +
                "2. За 1 день\n" +
                "3. За 1 неделю\n" +
                "4. Без напоминания";
        sender.sendAsync(new SendMessage(chatId.toString(), message));
    }

    private void cancelTaskUpdate(Long chatId) {
        updateContext.complete(chatId);
        sender.sendAsync(new SendMessage(chatId.toString(), "❗ Редактирование задачи отменено!"+
                "\n\nЕсли хотите вернуться к списку команд, используйте /help"));
    }


    private void completeTaskUpdate(Long chatId, SimpleTaskUpdateState state, boolean markAsCompleted) {
        Mono<SimpleTask> updateMono;

        if (markAsCompleted) {
            updateMono = taskService.markAsCompleted(state.getTaskId(), state.getUserId());
        } else if (state.getDescription() != null) {
            updateMono = taskService.updateTaskDescription(state.getTaskId(), state.getUserId(), state.getDescription());
        } else if (state.getComplexity() != null) {
            updateMono = taskService.updateTaskComplexity(state.getTaskId(), state.getUserId(), state.getComplexity());
        } else if (state.getDeadline() != null) {
            updateMono = taskService.updateTaskDeadline(state.getTaskId(), state.getUserId(), state.getDeadline());
        } else if (state.getReminder() != null) {
            updateMono = taskService.updateTaskReminder(state.getTaskId(), state.getUserId(), state.getReminder());
        } else {
            updateMono = Mono.empty();
        }

        updateMono.subscribe(
                task -> {
                    String successMessage = markAsCompleted ?
                            "✅ Задача выполнена!" : "🌟 Задача успешно изменена!" +
                            "\n\nЕсли хотите вернуться к списку команд, используйте /help";;
                    sender.sendAsync(new SendMessage(chatId.toString(), successMessage));
                    updateContext.complete(chatId);
                },
                error -> {
                    sender.sendAsync(new SendMessage(chatId.toString(),
                            "❌ Ошибка при обновлении задачи: " + error.getMessage()));
                    updateContext.complete(chatId);
                }
        );
    }

    private void processUserInputForRepeatingTasks(Long chatId, String input) {
        RepeatingTaskUpdateState state = repeatingTaskUpdateContext.getState(chatId);

        try {
            switch (state.getCurrentStep()) {
                case SELECT_FIELD:
                    int choice = Integer.parseInt(input);
                    if (choice < 1 || choice > 5) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Введите целое число в диапазоне от 1 до 5 в " +
                                        "зависимости от выбранного действия. Повторите ввод."));
                        return;
                    }

                    switch (choice) {
                        case 1:
                            state.setCurrentStep(RepeatingTaskUpdateStep.NEW_DESCRIPTION);
                            askForNewDescription(chatId);
                            break;
                        case 2:
                            state.setCurrentStep(RepeatingTaskUpdateStep.NEW_COMPLEXITY);
                            askForNewComplexity(chatId);
                            break;
                        case 3:
                            state.setCurrentStep(RepeatingTaskUpdateStep.NEW_FREQUENCY);
                            askForNewFrequency(chatId);
                            break;
                        case 4:
                            state.setCurrentStep(RepeatingTaskUpdateStep.NEW_START_DATE);
                            askForNewStartDate(chatId);
                            break;
                        case 5:
                            cancelTaskUpdateRepeatingTask(chatId);
                            break;
                    }
                    break;

                case NEW_DESCRIPTION:
                    if (input.length() > 200) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Описание задачи не должно превышать 200 символов. Повторите ввод."));
                        return;
                    }
                    state.setDescription(input);
                    completeRepeatingTaskUpdate(chatId, state);
                    break;

                case NEW_COMPLEXITY:
                    int complexity = Integer.parseInt(input);
                    if (complexity < 1 || complexity > 5) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Оцените сложность задачи, введя целое число в диапазоне от 1 до 5. Повторите ввод."));
                        return;
                    }
                    state.setComplexity(complexity);
                    completeRepeatingTaskUpdate(chatId, state);
                    break;

                case NEW_FREQUENCY:
                    int frequencyChoice = Integer.parseInt(input);
                    if (frequencyChoice < 1 || frequencyChoice > 4) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Введите целое число в диапазоне от 1 до 4 в зависимости от " +
                                        "выбранного действия. Повторите ввод."));
                        return;
                    }

                    RepeatingTask.RepeatFrequency frequency = TaskValidation.convertToFrequencyType(frequencyChoice);

                    state.setFrequency(frequency);
                    completeRepeatingTaskUpdate(chatId, state);
                    break;

                case NEW_START_DATE:
                    LocalDateTime startdatetime = TaskValidation.parseDateTime(input);
                    if (startdatetime == null || startdatetime.isBefore(LocalDateTime.now())) {
                        sender.sendAsync(new SendMessage(chatId.toString(),
                                "❌ Укажите корректную дату и время в формате дд.мм.гггг чч:мм, " +
                                        "не ранее текущего момента.\n" +
                                        "Повторите ввод."));
                        return;
                    }

                    state.setStartDateTime(startdatetime);
                    completeRepeatingTaskUpdate(chatId, state);
                    break;
            }
            repeatingTaskUpdateContext.updateState(chatId, state);
        } catch (NumberFormatException e) {
            sender.sendAsync(new SendMessage(chatId.toString(),
                    "❌ Пожалуйста, введите число."));
        }
    }

    private void cancelTaskUpdateRepeatingTask(Long chatId) {
        repeatingTaskUpdateContext.complete(chatId);
        sender.sendAsync(new SendMessage(chatId.toString(), "❗ Редактирование задачи отменено!"+
                "\n\nЕсли хотите вернуться к списку команд, используйте /help"));
    }


    private void askForNewFrequency(Long chatId) {
        String message = "🔁 Укажите новый период повторения задачи:\n" +
                "1. Ежечасно\n" +
                "2. Ежедневно\n" +
                "3. Еженедельно\n" +
                "4. Ежемесячно";
        sender.sendAsync(new SendMessage(chatId.toString(), message));
    }

    private void askForNewStartDate(Long chatId) {
        sender.sendAsync(new SendMessage(chatId.toString(),
                "🗓️ Укажите новую дату и время начала задачи (в формате дд.мм.гггг чч:мм) "));
    }

    private void completeRepeatingTaskUpdate(Long chatId, RepeatingTaskUpdateState state) {
        Mono<RepeatingTask> updateMono;

        if (state.getDescription() != null) {
            updateMono = repeatingTaskService.updateTaskDescription(state.getTaskId(), state.getUserId(),
                    state.getDescription());
        } else if (state.getComplexity() != null) {
            updateMono = repeatingTaskService.updateTaskComplexity(state.getTaskId(), state.getUserId(),
                    state.getComplexity());
        } else if (state.getFrequency() != null) {
            updateMono = repeatingTaskService.updateTaskFrequency(state.getTaskId(), state.getUserId(),
                    state.getFrequency());
        } else if (state.getStartDateTime() != null) {
            updateMono = repeatingTaskService.updateTaskStartDateTime(state.getTaskId(), state.getUserId(),
                    state.getStartDateTime());
        } else {
            updateMono = Mono.empty();
        }

        updateMono.subscribe(
                task -> {
                    String message =  "🌟 Задача успешно изменена!" +
                            "\n\nЕсли хотите вернуться к списку команд, используйте /help";;
                    sender.sendAsync(new SendMessage(chatId.toString(), message));
                    repeatingTaskUpdateContext.complete(chatId);
                },
                error -> {
                    sender.sendAsync(new SendMessage(chatId.toString(),
                            "❌ Ошибка при обновлении задачи: " + error.getMessage()));
                    repeatingTaskUpdateContext.complete(chatId);
                }
        );
    }

}
