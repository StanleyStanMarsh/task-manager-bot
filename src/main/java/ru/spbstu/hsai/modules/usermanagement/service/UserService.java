package ru.spbstu.hsai.modules.usermanagement.service;

import org.springframework.stereotype.Service;
import ru.spbstu.hsai.modules.usermanagement.model.User;
import ru.spbstu.hsai.modules.usermanagement.repository.UserRepository;


@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void registerIfAbsent(Long telegramId, String username, String firstName, String lastName) {
        userRepository.findByTelegramId(telegramId).ifPresentOrElse(
                user -> updateUserIfNeeded(user, username, firstName, lastName),
                () -> createNewUser(telegramId, username, firstName, lastName)
        );
    }

    private void createNewUser(Long telegramId, String username, String firstName, String lastName) {
        User user = new User(telegramId, "USER");
        setUserFields(user, username, firstName, lastName);
        userRepository.save(user);
    }

    private void updateUserIfNeeded(User user, String username, String firstName, String lastName) {
        if (needUpdate(user, username, firstName, lastName)) {
            setUserFields(user, username, firstName, lastName);
            userRepository.save(user);
        }
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
