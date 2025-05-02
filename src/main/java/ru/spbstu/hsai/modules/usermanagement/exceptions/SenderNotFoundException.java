package ru.spbstu.hsai.modules.usermanagement.exceptions;

public class SenderNotFoundException extends RuntimeException {
    public SenderNotFoundException(Long telegramId) {
        super(String.format("Sender user with telegramId %s is not found.", telegramId));
    }
}
