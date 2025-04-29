package ru.spbstu.hsai.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import ru.spbstu.hsai.infrastructure.server.BotProperties;
import ru.spbstu.hsai.infrastructure.server.ServerProperties;
import ru.spbstu.hsai.modules.check.HelloHandler;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@ComponentScan(basePackages = "ru.spbstu.hsai")
@Configuration
@EnableWebFlux
@PropertySource("classpath:application.properties")
@PropertySource("classpath:telegram.properties")
public class WebConfig {

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
    public RouterFunction<ServerResponse> routes(HelloHandler helloHandler) {
        return route(GET("/hello"), helloHandler::hello);
    }
}
