package ru.spbstu.hsai.api.commands.utils;

import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class FormattedRepeatingTask implements FormattedTask {

    private final RepeatingTask task;
    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public FormattedRepeatingTask(RepeatingTask task) {
        this.task = task;
    }

    @Override
    public String format(ZoneId userZoneId) {
        ZonedDateTime taskMoscowZonedStartDateTime = task.getStartDateTime().atZone(ZoneId.of("Europe/Moscow"));
        ZonedDateTime taskUserZonedStartDateTime = taskMoscowZonedStartDateTime.withZoneSameInstant(userZoneId);

        ZonedDateTime nextExecutionMoscowZonedDateTime = task.getNextExecution().atZone(ZoneId.of("Europe/Moscow"));
        ZonedDateTime nextExecutionUserZonedDateTime = nextExecutionMoscowZonedDateTime.withZoneSameInstant(userZoneId);

        return String.format(
                "✅Задача создана!\n" +
                        "🆔 ID: <code>%s</code>\n" +
                        "📌 Описание: %s\n" +
                        "📊 Сложность: %d\n" +
                        "🔁 Периодичность: %s\n" +
                        "🕒 Начало: %s\n" +
                        "⏳ Следующее выполнение: %s",
                task.getId(),
                task.getDescription(),
                task.getComplexity(),
                task.getFrequency().getDisplayName(),
                taskUserZonedStartDateTime.toLocalDateTime().format(
                        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                ),
                nextExecutionUserZonedDateTime.toLocalDateTime().format(
                        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                )
        );
    }
}
