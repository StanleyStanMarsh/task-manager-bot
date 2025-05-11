package ru.spbstu.hsai.repeatingtaskmanagment;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Document(collection = "repeatingtasks")
@CompoundIndex(name = "next_exec_idx", def = "{'nextExecution': 1}")
public class RepeatingTask {
    @Id
    private String id;

    private String userId;
    private String description;
    private int complexity;
    private RepeatFrequency frequency;
    private LocalDateTime startDateTime;

    @Field("nextExecution")
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


    public void calculateNextExecution(LocalDateTime currentTime) {
        // Вычисляем сколько периодов пропущено
        long periodsToSkip = calculatePeriodsToSkip(currentTime);

        // Прибавляем нужное количество периодов
        switch (frequency) {
            case HOURLY:
                this.nextExecution = nextExecution.plusHours(periodsToSkip).truncatedTo(ChronoUnit.MINUTES);
                break;
            case DAILY:
                this.nextExecution = nextExecution.plusDays(periodsToSkip).truncatedTo(ChronoUnit.MINUTES);
                break;
            case WEEKLY:
                this.nextExecution = nextExecution.plusWeeks(periodsToSkip).truncatedTo(ChronoUnit.MINUTES);
                break;
            case MONTHLY:
                this.nextExecution = nextExecution.plusMonths(periodsToSkip).truncatedTo(ChronoUnit.MINUTES);
                break;
        }
    }

    private long calculatePeriodsToSkip(LocalDateTime currentTime) {
        Duration duration = Duration.between(nextExecution, currentTime);

        return switch (frequency) {
            case HOURLY -> duration.toHours() + 1;
            case DAILY -> duration.toDays() + 1;
            case WEEKLY -> duration.toDays() / 7 + 1;
            case MONTHLY -> {
                long months = ChronoUnit.MONTHS.between(
                        nextExecution.toLocalDate(),
                        currentTime.toLocalDate()
                );
                yield months + 1;
            }
        };
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
