package ru.spbstu.hsai.infrastructure.db;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;
import ru.spbstu.hsai.modules.usermanagement.service.UserService;

import javax.annotation.PostConstruct;

@Configuration
public class SuperAdminInitializer {

    private final UserService userService;

    @Value("${superadmin.telegramId}")
    private Long telegramId;
    @Value("${superadmin.username}")
    private String username;
    @Value("${superadmin.firstName}")
    private String firstName;
    @Value("${superadmin.lastName}")
    private String lastName;

    public SuperAdminInitializer(UserService userService) {
        this.userService = userService;
    }

    @PostConstruct
    public void init() {
        // Запускаем в фоновом реактивном потоке, чтобы не блокировать старт контекста
        Disposable d = userService.ensureSuperAdmin(telegramId, username, firstName, lastName)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(u -> System.out.println("Super-admin инициализирован: " + u.getTelegramId()))
                .doOnError(err -> System.err.println("Не удалось инициализировать super-admin: " + err.getMessage()))
                .subscribe();
    }
}