package ru.spbstu.hsai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import ru.spbstu.hsai.telegram.BotProperties;
import ru.spbstu.hsai.infrastructure.ServerProperties;
import ru.spbstu.hsai.authors.AuthorsController;
import ru.spbstu.hsai.check.HelloHandler;
import ru.spbstu.hsai.usermanagement.UserController;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@ComponentScan(basePackages = "ru.spbstu.hsai")
@Configuration
@EnableWebFlux
@PropertySource("classpath:application.properties")
@PropertySource("classpath:telegram.properties")
public class WebConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder());
        configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder());
    }

    @Bean
    public ServerProperties serverProperties(
            @Value("${server.host}") String host,
            @Value("${server.port}") int port) {
        return new ServerProperties(host, port);
    }

    @Bean
    public BotProperties botProperties(
            @Value("${telegram.bot.token}") String token,
            @Value("${telegram.bot.username}") String botUsername) {
        return new BotProperties(token, botUsername);
    }

    @Bean
    public RouterFunction<ServerResponse> routes(
            HelloHandler helloHandler,
            UserController userController,
            AuthorsController authorsController
    ) {
        return route(GET("/hello"), helloHandler::hello)
                .andRoute(PATCH("/users/{telegramId}/promote"), userController::promote)
                .andRoute(PATCH("/self_demote"), userController::selfDemote)
                .andRoute(PATCH("/users/{telegramId}/demote"), userController::demote)
                .andRoute(GET("/authors"),authorsController::authors)
                .andRoute(GET("/users"), userController::listUsersExcluding);
    }
}
