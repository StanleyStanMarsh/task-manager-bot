package ru.spbstu.hsai.modules.simpletaskmanagment.repository;

import org.springframework.data.mongodb.repository.DeleteQuery;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.simpletaskmanagment.model.SimpleTask;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import reactor.core.publisher.Flux;
import java.time.LocalDate;

@Repository
public interface SimpleTaskRepository extends ReactiveMongoRepository<SimpleTask, String> {
    // Для /mytasks (все активные задачи пользователя)
    @Query("{ 'userId': ?0, 'isCompleted': false }")
    Flux<SimpleTask> findActiveTasksByUserId(String userId);

    // Для /today (активные задачи на сегодня)
    @Query("{ 'userId': ?0, 'deadline': ?1, 'isCompleted': false }")
    Flux<SimpleTask> findTasksByDate(String userId, LocalDate date);

    // Для /week (задачи на текущую неделю)
    @Query("{ 'userId': ?0, 'deadline': { $gte: ?1, $lte: ?2 }, 'isCompleted': false }")
    Flux<SimpleTask> findTasksForWeek(String userId, LocalDate startOfWeek, LocalDate endOfWeek);

    // Для /date (задачи на указанную дату)
    @Query("{ 'userId': ?0, 'deadline': ?1, 'isCompleted': false }")
    Flux<SimpleTask> findTasksByCustomDate(String userId, LocalDate date);

    // Для /deletetask
    @DeleteQuery("{ '_id': ?0, 'userId': ?1 }")
    Mono<Long> deleteByIdAndUserId(String id, String userId);


    // Для поиска существующей задачи
    @Query("{ 'userId': ?0, 'description': ?1, 'complexity': ?2, 'deadline': ?3, 'reminder': ?4 }")
    Mono<SimpleTask> findTask(String userId, String description, int complexity,
                                       LocalDate deadline, SimpleTask.ReminderType reminder);


    // Для поиска задачи по ID и пользователю
    @Query("{ '_id': ?0, 'userId': ?1 }")
    Mono<SimpleTask> findByIdAndUserId(String taskId, String userId);


    // Для поиска просроченных задач (мб можно будет использовать для напоминаний)
    //@Query("{ 'userId': ?0, 'deadline': { $lt: ?1 }, 'isCompleted': false }")
    //Flux<SimpleTask> findOverdueTasks(String userId, LocalDate currentDate);

}

