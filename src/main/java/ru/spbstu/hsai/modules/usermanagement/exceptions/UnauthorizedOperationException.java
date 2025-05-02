package ru.spbstu.hsai.modules.usermanagement.exceptions;

public class UnauthorizedOperationException extends RuntimeException {
    public UnauthorizedOperationException(Long telegramId) {
        super(String.format("User with telegramId %s is unauthorized.", telegramId));
    }
}
