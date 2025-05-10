package ru.spbstu.hsai.usermanagement;

import reactor.core.publisher.Mono;

public interface UserServiceInterface {
    Mono<User> findById(String id);
    Mono<User> findByTelegramId(Long telegramId);
    Mono<User> ensureSuperAdmin(Long telegramId, String username, String firstName, String lastName, String hashedPassword);
    Mono<Void> updateTimezone(Long telegramId, String timezone);
    Mono<Void> registerIfAbsent(Long telegramId, String username, String firstName, String lastName);
}
