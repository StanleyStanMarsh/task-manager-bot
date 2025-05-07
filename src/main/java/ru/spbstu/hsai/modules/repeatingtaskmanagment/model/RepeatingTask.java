package ru.spbstu.hsai.modules.repeatingtaskmanagment.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Document(collection = "repeatingtasks")
public class RepeatingTask {
    @Id
    private String id;

    private String userId;
    private String description;
    private int complexity;
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

    public RepeatingTask() {}

    public RepeatingTask(String userId, String description, int complexity,
                         RepeatFrequency frequency, LocalDateTime startDateTime) {
        this.userId = userId;
        this.description = description;
        this.complexity = complexity;
        this.frequency = frequency;
        this.startDateTime = startDateTime;
        this.nextExecution = startDateTime;
    }

    // Геттеры и сеттеры
    public RepeatFrequency getFrequency() {return frequency;}
    public void setFrequency(RepeatFrequency frequency) {this.frequency = frequency;}

    public LocalDateTime getStartDateTime() {return startDateTime;}
    public void setStartDateTime(LocalDateTime startDateTime) {this.startDateTime = startDateTime;}

    public LocalDateTime getNextExecution() {return nextExecution;}
    public void setNextExecution(LocalDateTime nextExecution) {this.nextExecution = nextExecution;}

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public int getComplexity() {
        return complexity;
    }
    public void setComplexity(int complexity) {
        this.complexity = complexity;
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
