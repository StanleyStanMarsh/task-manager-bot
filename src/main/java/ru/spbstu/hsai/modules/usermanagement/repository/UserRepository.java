package ru.spbstu.hsai.modules.usermanagement.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import ru.spbstu.hsai.modules.usermanagement.model.User;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByTelegramId(Long telegramId);  // Поиск пользователя по Telegram ID
}
