package ru.spbstu.hsai.modules.usermanagement.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.usermanagement.model.User;
import ru.spbstu.hsai.modules.usermanagement.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mono<Void> registerIfAbsent(Long telegramId, String username, String firstName, String lastName) {
        return userRepository.findByTelegramId(telegramId)
                .flatMap(user -> {
                    if (needUpdate(user, username, firstName, lastName)) {
                        setUserFields(user, username, firstName, lastName);
                        return userRepository.save(user).then();
                    } else {
                        return Mono.empty(); // no update needed
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    User user = new User(telegramId, "USER");
                    setUserFields(user, username, firstName, lastName);
                    return userRepository.save(user).then();
                }));
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
        return (username != null && !username.equals(user.getUsername())) ||
                (firstName != null && !firstName.equals(user.getFirstName())) ||
                (lastName != null && !lastName.equals(user.getLastName()));
    }
}