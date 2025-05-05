package ru.spbstu.hsai.modules.simpletaskmanagment.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Document(collection = "simpletasks")
public class SimpleTask {
    @Id
    private String id;

    private String userId;
    private String description;
    private int complexity;
    private LocalDate deadline;
    private ReminderType reminder;
    private boolean isCompleted = false;


    // Enum для типов напоминаний
    public enum ReminderType {
        ONE_HOUR_BEFORE("За 1 час"),
        ONE_DAY_BEFORE("За 1 день"),
        ONE_WEEK_BEFORE("За 1 неделю"),
        NO_REMINDER("Без напоминания");

        private final String displayName;

        ReminderType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Конструкторы
    public SimpleTask() {}

    public SimpleTask(String userId, String description, int complexity,
                      LocalDate deadline, ReminderType reminder) {
        this.userId = userId;
        this.description = description;
        this.complexity = complexity;
        this.deadline = deadline;
        this.reminder = reminder;
    }

    // Геттеры и сеттеры
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

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate date) {
        this.deadline = date;
    }

    public ReminderType getReminder() {
        return reminder;
    }

    public void setReminder(ReminderType reminder) {
        this.reminder = reminder;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    @Override
    public String toString() {
        return "🆔 ID: <code>" + id + "</code>\n" +
                "📌Описание: " + description + "\n" +
                "📊Сложность: " + complexity + "\n" +
                "🗓️Дедлайн: " + deadline.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "\n" +
                "⏰Напоминание: " + reminder.getDisplayName();
    }


}