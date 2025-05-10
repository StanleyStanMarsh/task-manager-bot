package ru.spbstu.hsai.infrastructure.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.scheduler.Schedulers;
import ru.spbstu.hsai.usermanagement.UserServiceInterface;
import ru.spbstu.hsai.usermanagement.service.UserService;

import javax.annotation.PostConstruct;

@Configuration
public class SuperAdminInitializer {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminInitializer.class);

    private final UserServiceInterface userService;
    private final PasswordEncoder passwordEncoder;

    @Value("${superadmin.telegramId}")
    private Long telegramId;
    @Value("${superadmin.username}")
    private String username;
    @Value("${superadmin.firstName}")
    private String firstName;
    @Value("${superadmin.lastName}")
    private String lastName;
    @Value("${superadmin.password}")
    private String rawPassword;

    public SuperAdminInitializer(UserServiceInterface userService,
                                 PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        String hashed = passwordEncoder.encode(rawPassword);
        userService.ensureSuperAdmin(telegramId, username, firstName, lastName, hashed)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(u -> log.info("Super-admin инициализирован: {}", u.getTelegramId()))
                .doOnError(err -> log.error("Не удалось инициализировать super-admin: {}", err.getMessage()))
                .subscribe();
    }
}