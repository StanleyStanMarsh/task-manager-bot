package ru.spbstu.hsai.modules.usermanagement.repository;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.usermanagement.model.User;

@Repository
public interface UserRepository extends ReactiveMongoRepository<User, String> {
    @Query("{ 'telegramId': ?0 }")
    Mono<User> findByTelegramId(Long telegramId);
}
