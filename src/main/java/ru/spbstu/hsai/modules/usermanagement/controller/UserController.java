package ru.spbstu.hsai.modules.usermanagement.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.usermanagement.dto.FormattedUser;
import ru.spbstu.hsai.modules.usermanagement.exceptions.*;
import ru.spbstu.hsai.modules.usermanagement.service.UserService;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                        _ -> ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString())), BaseResponse.class)
                )
                .onErrorResume(
                        SenderNotFoundException.class,
                        _ -> ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString())), BaseResponse.class)
                )
                .onErrorResume(
                        TargetNotFoundException.class,
                        _ -> ServerResponse.status(HttpStatus.NOT_FOUND)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.NOT_FOUND.value(), Instant.now().toString())), BaseResponse.class)
                )
                .onErrorResume(
                        AlreadyGrantedException.class,
                        _ -> ServerResponse.status(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.BAD_REQUEST.value(), Instant.now().toString())), BaseResponse.class)
                );
    }

    public Mono<ServerResponse> selfDemote(ServerRequest request) {
        Long senderId = Long.valueOf(request.queryParam("senderId")
                .orElseThrow(() -> new IllegalArgumentException("senderId query parameter is required")));
        return userService.selfDemoteToUser(senderId)
                .then(
                        ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.OK.value(), Instant.now().toString())), BaseResponse.class)
                )
                .onErrorResume(
                        SenderNotFoundException.class,
                        _ -> ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString())), BaseResponse.class)
                )
                .onErrorResume(
                        UnauthorizedOperationException.class,
                        _ -> ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString())), BaseResponse.class)
                )
                .onErrorResume(
                        SuperAdminDemoteException.class,
                        _ -> ServerResponse.status(HttpStatus.CONFLICT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.CONFLICT.value(), Instant.now().toString())), BaseResponse.class)
                );
    }

    public Mono<ServerResponse> demote(ServerRequest request) {
        Long senderId = Long.valueOf(request.queryParam("senderId")
                .orElseThrow(() -> new IllegalArgumentException("senderId query parameter is required")));
        Long targetId = Long.valueOf(request.pathVariable("telegramId"));
        return userService.demoteToUser(senderId, targetId)
                .then(
                        ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.OK.value(), Instant.now().toString())), BaseResponse.class)
                )
                .onErrorResume(
                        UnauthorizedOperationException.class,
                        _ -> ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString())), BaseResponse.class)
                )
                .onErrorResume(
                        SenderNotFoundException.class,
                        _ -> ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString())), BaseResponse.class)
                )
                .onErrorResume(
                        TargetNotFoundException.class,
                        _ -> ServerResponse.status(HttpStatus.NOT_FOUND)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.NOT_FOUND.value(), Instant.now().toString())), BaseResponse.class)
                )
                .onErrorResume(
                        SuperAdminDemoteException.class,
                        _ -> ServerResponse.status(HttpStatus.CONFLICT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.CONFLICT.value(), Instant.now().toString())), BaseResponse.class)
                )
                .onErrorResume(
                        AlreadyUserException.class,
                        _ -> ServerResponse.status(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(new BaseResponse(HttpStatus.BAD_REQUEST.value(), Instant.now().toString())), BaseResponse.class)
                );
    }

    public Mono<ServerResponse> listUsersExcluding(ServerRequest request) {
        Long senderId = Long.valueOf(request.queryParam("senderId")
                .orElseThrow(() -> new IllegalArgumentException("senderId query parameter is required")));
        String format = request.queryParam("format").orElse("json");

        return userService.getAllUsersExceptSuperAdmin(senderId)
                .collectList()
                .flatMap(usersList -> {
                    List<FormattedUser> formattedUsers = usersList.stream()
                            .map(user -> new FormattedUser(user.getId(), user.getUsername(), user.getRole()))
                            .toList();

                    if ("csv".equalsIgnoreCase(format)) {
                        String csv = formattedUsers.stream()
                                .map(u -> String.format("%s,%s,%s",
                                        safe(u.id()),
                                        safe(u.username()),
                                        safe(u.role())))
                                .collect(Collectors.joining("\n", "id,username,role\n", ""));
                        return ServerResponse.ok()
                                .contentType(MediaType.valueOf("text/csv"))
                                .bodyValue(csv);
                    } else {
                        UsersListResponse response = new UsersListResponse(
                                HttpStatus.OK.value(),
                                formattedUsers,
                                formattedUsers.size(),
                                Instant.now().toString()
                        );
                        return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(response);
                    }
                })
                .onErrorResume(SenderNotFoundException.class, e ->
                        ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString()))
                )
                .onErrorResume(UnauthorizedOperationException.class, e ->
                        ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString()))
                );
    }

    private String safe(String value) {
        return value == null ? "" : value.replace(",", "");
    }
}
