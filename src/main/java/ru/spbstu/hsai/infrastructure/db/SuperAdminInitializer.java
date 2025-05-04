package ru.spbstu.hsai.infrastructure.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;
import ru.spbstu.hsai.modules.usermanagement.service.UserService;

import javax.annotation.PostConstruct;

@Configuration
public class SuperAdminInitializer {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminInitializer.class);

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
                .doOnSuccess(u -> log.info("Super-admin инициализирован: {}", u.getTelegramId()))
                .doOnError(err -> log.error("Не удалось инициализировать super-admin: {}", err.getMessage()))
                .subscribe();
    }
}