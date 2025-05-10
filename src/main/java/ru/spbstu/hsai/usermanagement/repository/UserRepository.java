package ru.spbstu.hsai.usermanagement.repository;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.usermanagement.User;

@Repository
public interface UserRepository extends ReactiveMongoRepository<User, String> {
    @Query("{ 'telegramId': ?0 }")
    Mono<User> findByTelegramId(Long telegramId);

    Flux<User> findByTelegramIdNot(Long telegramId);
}
