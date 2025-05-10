package ru.spbstu.hsai.usermanagement.exceptions;

public class AlreadyGrantedException extends RuntimeException {
    public AlreadyGrantedException(Long telegramId) {
        super(String.format("Target user with telegramId %s already has ADMIN role.", telegramId));
    }
}
