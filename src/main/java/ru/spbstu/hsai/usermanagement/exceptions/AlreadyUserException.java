package ru.spbstu.hsai.usermanagement.exceptions;

public class AlreadyUserException extends RuntimeException {
    public AlreadyUserException(Long telegramId) {
        super(String.format("Target user with telegramId %s already has USER role.", telegramId));
    }
}
