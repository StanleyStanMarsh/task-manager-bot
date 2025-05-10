package ru.spbstu.hsai.api.commands.utils;

import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTask;
import ru.spbstu.hsai.simpletaskmanagment.SimpleTask;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class TaskValidation {
    public static SimpleTask.ReminderType convertToReminderType(int choice) {
        return switch (choice) {
            case 1 -> SimpleTask.ReminderType.ONE_HOUR_BEFORE;
            case 2 -> SimpleTask.ReminderType.ONE_DAY_BEFORE;
            case 3 -> SimpleTask.ReminderType.ONE_WEEK_BEFORE;
            default -> SimpleTask.ReminderType.NO_REMINDER;
        };
    }

    public static boolean isReminderValid(LocalDate deadline, SimpleTask.ReminderType reminder) {
        LocalDate reminderDate = calculateReminderDate(deadline, reminder);
        return reminderDate.isAfter(LocalDate.now());
    }

    public static LocalDate calculateReminderDate(LocalDate deadline, SimpleTask.ReminderType reminder) {
        return switch (reminder) {
            case ONE_HOUR_BEFORE -> deadline; // Для часового напоминания проверяем сам дедлайн
            case ONE_DAY_BEFORE -> deadline.minusDays(1);
            case ONE_WEEK_BEFORE -> deadline.minusWeeks(1);
            case NO_REMINDER -> deadline; // Для "без напоминания" проверка не нужна
        };
    }

    public static LocalDate parseDate(String input) {
        try {
            return LocalDate.parse(input, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static RepeatingTask.RepeatFrequency convertToFrequencyType(int choice) {
        return switch (choice) {
            case 1 -> RepeatingTask.RepeatFrequency.HOURLY;
            case 2 -> RepeatingTask.RepeatFrequency.DAILY;
            case 3 -> RepeatingTask.RepeatFrequency.WEEKLY;
            default -> RepeatingTask.RepeatFrequency.MONTHLY;
        };
    }

    public static LocalDateTime parseDateTime(String input) {
        try {
            return LocalDateTime.parse(input, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        } catch (DateTimeParseException e) {
            return null;
        }

    }
}
