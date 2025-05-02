package ru.spbstu.hsai.modules.usermanagement.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.usermanagement.exceptions.AlreadyGrantedException;
import ru.spbstu.hsai.modules.usermanagement.exceptions.SenderNotFoundException;
import ru.spbstu.hsai.modules.usermanagement.exceptions.TargetNotFoundException;
import ru.spbstu.hsai.modules.usermanagement.exceptions.UnauthorizedOperationException;
import ru.spbstu.hsai.modules.usermanagement.service.UserService;

import java.time.Instant;
import java.util.Map;

@Controller
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public Mono<ServerResponse> promote(ServerRequest request) {
        Long senderId = Long.valueOf(request.queryParam("senderId")
                .orElseThrow(() -> new IllegalArgumentException("senderId query parameter is required")));
        Long targetId = Long.valueOf(request.pathVariable("telegramId"));
        return userService.promoteToAdmin(senderId, targetId)
                .then(
                        ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.OK.value(), Instant.now().toString())), BaseResponse.class)
                )
                .onErrorResume(
                        UnauthorizedOperationException.class,
                        e -> ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString())), BaseResponse.class)
                )
                .onErrorResume(
                        SenderNotFoundException.class,
                        e -> ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString())), BaseResponse.class)
                )
                .onErrorResume(
                        TargetNotFoundException.class,
                        e -> ServerResponse.status(HttpStatus.NOT_FOUND)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.NOT_FOUND.value(), Instant.now().toString())), BaseResponse.class)
                )
                .onErrorResume(
                        AlreadyGrantedException.class,
                        e -> ServerResponse.status(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.BAD_REQUEST.value(), Instant.now().toString())), BaseResponse.class)
                );
    }
}
