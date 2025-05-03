package ru.spbstu.hsai.modules.usermanagement.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.usermanagement.exceptions.*;
import ru.spbstu.hsai.modules.usermanagement.model.User;
import ru.spbstu.hsai.modules.usermanagement.repository.UserRepository;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;

    @Value("${superadmin.telegramId}")
    private String superAdminId;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mono<Void> registerIfAbsent(Long telegramId, String username, String firstName, String lastName) {
        System.out.println("Checking user with telegramId=" + telegramId);
        return userRepository.findByTelegramId(telegramId)
                .doOnNext(user -> {
                    System.out.println("Найден пользователь: telegramId=" + telegramId + ", user=" + user);
                    log.info("User found: telegramId={}, user={}", telegramId, user);
                })
                .doOnSuccess(user -> {
                    if (user == null) {
                        System.out.println("No user found with telegramId=" + telegramId);
                        log.warn("No user found with telegramId={}", telegramId);
                    } else {
                        System.out.println("User found and processed: telegramId=" + telegramId);
                    }
                })
                .doOnError(error -> {
                    System.out.println("Error finding user: " + error.getMessage());
                    log.error("Error finding user with telegramId={}: {}", telegramId, error.getMessage());
                })
                .switchIfEmpty(Mono.defer(() -> {
                    System.out.println("Creating new user with telegramId=" + telegramId);
                    User user = new User(telegramId, "USER");
                    setUserFields(user, username, firstName, lastName); // Устанавливаем поля сразу
                    return userRepository.save(user)
                            .doOnSuccess(savedUser -> System.out.println("New user registered: telegramId=" + savedUser.getTelegramId()))
                            .doOnError(e -> System.out.println("Failed to register: " + e.getMessage()))
                            .thenReturn(user); // Возвращаем того же пользователя
                }))
                .flatMap(user -> {
                    System.out.println("Entering flatMap for telegramId=" + telegramId);
                    if (needUpdate(user, username, firstName, lastName)) {
                        System.out.println("Updating user with telegramId=" + telegramId);
                        setUserFields(user, username, firstName, lastName);
                        return userRepository.save(user)
                                .doOnSuccess(savedUser -> System.out.println("User updated: telegramId=" + savedUser.getTelegramId()))
                                .doOnError(e -> System.out.println("Failed to update: " + e.getMessage()))
                                .then();
                    } else {
                        System.out.println("No update needed for telegramId=" + telegramId);
                        return Mono.empty(); // Завершаем без дополнительного сохранения
                    }
                })
                .then(); // Убеждаемся, что возвращается Mono<Void>
    }

    public Mono<User> ensureSuperAdmin(Long telegramId, String username, String firstName, String lastName) {
        return userRepository.findByTelegramId(telegramId)
                .flatMap(existing -> {
                    // уже есть — ничего не меняем, возвращаем
                    return Mono.just(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    User admin = new User(telegramId, "ADMIN");
                    admin.setUsername(username);
                    admin.setFirstName(firstName);
                    admin.setLastName(lastName);
                    return userRepository.save(admin);
                }));
    }

    private void setUserFields(User user, String username, String firstName, String lastName) {
        if (username != null) user.setUsername(username);
        if (firstName != null) user.setFirstName(firstName);
        if (lastName != null) user.setLastName(lastName);
    }

    private boolean needUpdate(User user, String username, String firstName, String lastName) {
        System.out.println("Checking update: username=" + username + ", firstName=" + firstName + ", lastName=" + lastName);
        return (username != null && !username.equals(user.getUsername())) ||
                (firstName != null && !firstName.equals(user.getFirstName())) ||
                (lastName != null && !lastName.equals(user.getLastName()));
    }

    public Mono<Void> promoteToAdmin(Long senderId, Long targetId) {
        return userRepository.findByTelegramId(senderId)
                .switchIfEmpty(Mono.error(new SenderNotFoundException(senderId)))
                .flatMap(senderUser -> {
                    if (!"ADMIN".equals(senderUser.getRole())) {
                        return Mono.error(new UnauthorizedOperationException(senderId));
                    }
                    return userRepository.findByTelegramId(targetId)
                            .switchIfEmpty(Mono.error(new TargetNotFoundException(targetId)))
                            .flatMap(targetUser -> {
                                if ("ADMIN".equals(targetUser.getRole())) {
                                    return Mono.error(new AlreadyGrantedException(targetId));
                                }
                                targetUser.setRole("ADMIN");
                                return userRepository.save(targetUser).then();
                            });
                });
    }

    public Mono<Void> selfDemoteToUser(Long senderId) {
        return userRepository.findByTelegramId(senderId)
                .switchIfEmpty(Mono.error(new SenderNotFoundException(senderId)))
                .flatMap(senderUser -> {
                    if (!"ADMIN".equals(senderUser.getRole())) {
                        return Mono.error(new UnauthorizedOperationException(senderId));
                    }
                    if (superAdminId.equals(senderId.toString())) {
                        return Mono.error(new SuperAdminDemoteException());
                    }
                    senderUser.setRole("USER");
                    return userRepository.save(senderUser).then();
                });
    }
}