package ru.spbstu.hsai.usermanagement.exceptions;

public class TargetNotFoundException extends RuntimeException {
    public TargetNotFoundException(Long telegramId) {
        super(String.format("Target user with telegramId %s is not found.", telegramId));
    }
}
