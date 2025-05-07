package ru.spbstu.hsai.modules.repeatingtaskmanagment.model;

import org.springframework.data.mongodb.core.mapping.Document;
import ru.spbstu.hsai.modules.simpletaskmanagment.model.SimpleTask;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Document(collection = "repeatingtasks")
public class RepeatingTask extends SimpleTask {
    private RepeatFrequency frequency;
    private LocalDateTime startDateTime;
    private LocalDateTime nextExecution;

    public enum RepeatFrequency {
        HOURLY("Ежечасно"),
        DAILY("Ежедневно"),
        WEEKLY("Еженедельно"),
        MONTHLY("Ежемесячно");

        private final String displayName;

        RepeatFrequency(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public RepeatingTask(String userId, String description, int complexity,
                         RepeatFrequency frequency, LocalDateTime startDateTime) {
        super(userId, description, complexity, startDateTime.toLocalDate(), null);
        this.frequency = frequency;
        this.startDateTime = startDateTime;
        this.nextExecution = startDateTime;
    }

    // Геттеры и сеттеры
    public RepeatFrequency getFrequency() {
        return frequency;
    }

    public void setFrequency(RepeatFrequency frequency) {
        this.frequency = frequency;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public LocalDateTime getNextExecution() {
        return nextExecution;
    }

    public void setNextExecution(LocalDateTime nextExecution) {
        this.nextExecution = nextExecution;
    }


    // Метод для вычисления следующего выполнения
    public void calculateNextExecution() {
        switch (frequency) {
            case HOURLY:
                this.nextExecution = nextExecution.plusHours(1);
                break;
            case DAILY:
                this.nextExecution = nextExecution.plusDays(1);
                break;
            case WEEKLY:
                this.nextExecution = nextExecution.plusWeeks(1);
                break;
            case MONTHLY:
                this.nextExecution = nextExecution.plusMonths(1);
                break;
        }
    }

    @Override
    public String toString() {
        return "🆔 ID: <code>" + getId() + "</code>\n" +
                "📌 Описание: " + getDescription() + "\n" +
                "📊 Сложность: " + getComplexity() + "\n" +
                "🔁 Периодичность: " + frequency.getDisplayName() + "\n" +
                "🕒 Начало: " + startDateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "\n" +
                "⏳ Следующее выполнение: " + nextExecution.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

}

