package ru.spbstu.hsai.api.commands.utils;

import ru.spbstu.hsai.modules.simpletaskmanagment.model.SimpleTask;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class FormattedSimpleTask implements FormattedTask {
    private final SimpleTask task;
    private static final DateTimeFormatter DEADLINE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public FormattedSimpleTask(SimpleTask task) {
        this.task = task;
    }

    @Override
    public String format(ZoneId userZoneId) {
        // Для SimpleTask часовой пояс не нужен — дедлайн хранится как LocalDate
        return String.format(
                "🆔 ID: <code>%s</code>%n" +
                        "📌 Описание: %s%n" +
                        "📊 Сложность: %d%n" +
                        "🗓️ Дедлайн: %s%n" +
                        "⏰ Напоминание: %s",
                task.getId(),
                task.getDescription(),
                task.getComplexity(),
                task.getDeadline().format(DEADLINE_FMT),
                task.getReminder().getDisplayName()
        );
    }
}
