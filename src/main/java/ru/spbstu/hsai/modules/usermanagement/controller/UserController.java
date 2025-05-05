package ru.spbstu.hsai.modules.usermanagement.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.usermanagement.dto.FormattedUser;
import ru.spbstu.hsai.modules.usermanagement.dto.PromoteRequest;
import ru.spbstu.hsai.modules.usermanagement.exceptions.*;
import ru.spbstu.hsai.modules.usermanagement.service.UserService;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public Mono<ServerResponse> promote(ServerRequest request) {
        Mono<PromoteRequest> bodyMono = request
                .bodyToMono(PromoteRequest.class)
                .doOnSuccess(success -> log.info("Success after bodyToMono: {}", success))
                .doOnError(error -> log.error("Error after bodyToMono: {}", error.getMessage(), error))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Request body is required")));

        Mono<Authentication> authMono = request
                .principal()
                .doOnSuccess(success -> log.info("Success after principal: {}", success))
                .doOnError(error -> log.error("Error after principal: {}", error.getMessage(), error))
                .cast(Authentication.class)
                .switchIfEmpty(Mono.error(new UnauthorizedOperationException(-1L)));

        return bodyMono
                .flatMap(req -> authMono.flatMap(auth -> {
                    Long senderId = Long.valueOf(auth.getName());
                    Long targetId = Long.valueOf(request.pathVariable("telegramId"));
                    log.info("Authenticated senderId={}, req={}", senderId, req);
                    return userService.promoteToAdminWithPassword(senderId, targetId, req.getPassword());
                }))
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new BaseResponse(HttpStatus.OK.value(), Instant.now().toString())))
                .onErrorResume(IllegalArgumentException.class, e ->
                        ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.BAD_REQUEST.value(), Instant.now().toString())))
                .onErrorResume(UnauthorizedOperationException.class, e ->
                        ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString())))
                .onErrorResume(SenderNotFoundException.class, e ->
                        ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString())))
                .onErrorResume(TargetNotFoundException.class, e ->
                        ServerResponse.status(HttpStatus.NOT_FOUND)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.NOT_FOUND.value(), Instant.now().toString())))
                .onErrorResume(AlreadyGrantedException.class, e ->
                        ServerResponse.status(HttpStatus.CONFLICT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.CONFLICT.value(), Instant.now().toString())))
                .onErrorResume(ServerWebInputException.class, e ->
                        ServerResponse.badRequest()
                                .contentType(MediaType.TEXT_PLAIN)
                                .bodyValue(HttpStatus.BAD_REQUEST.value() + ": Invalid request body: " + e.getMessage()))
                .onErrorResume(Exception.class, e ->
                        ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .contentType(MediaType.TEXT_PLAIN)
                                .bodyValue(HttpStatus.INTERNAL_SERVER_ERROR.value() + ": Unexpected error: " + e.getMessage()));
    }

    public Mono<ServerResponse> selfDemote(ServerRequest request) {
        return request.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .map(Long::valueOf)
                .flatMap(userService::selfDemoteToUser)
                .then(
                        ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.OK.value(), Instant.now().toString()))
                )
                .onErrorResume(SenderNotFoundException.class,
                        _ -> ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString()))
                )
                .onErrorResume(UnauthorizedOperationException.class,
                        _ -> ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString()))
                )
                .onErrorResume(SuperAdminDemoteException.class,
                        _ -> ServerResponse.status(HttpStatus.CONFLICT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.CONFLICT.value(), Instant.now().toString()))
                );
    }

    public Mono<ServerResponse> demote(ServerRequest request) {
        Mono<Authentication> authMono = request
                .principal()
                .doOnSuccess(auth -> log.info("Authenticated principal: {}", auth))
                .doOnError(error -> log.error("Error retrieving principal: {}", error.getMessage(), error))
                .cast(Authentication.class)
                .switchIfEmpty(Mono.error(new UnauthorizedOperationException(-1L)));

        return authMono.flatMap(auth -> {
                    Long senderId = Long.valueOf(auth.getName());
                    Long targetId = Long.valueOf(request.pathVariable("telegramId"));
                    log.info("Demotion request from senderId={} to targetId={}", senderId, targetId);
                    return userService.demoteToUser(senderId, targetId);
                })
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new BaseResponse(HttpStatus.OK.value(), Instant.now().toString())))
                .onErrorResume(UnauthorizedOperationException.class, e ->
                        ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString())))
                .onErrorResume(SenderNotFoundException.class, e ->
                        ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.UNAUTHORIZED.value(), Instant.now().toString())))
                .onErrorResume(TargetNotFoundException.class, e ->
                        ServerResponse.status(HttpStatus.NOT_FOUND)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.NOT_FOUND.value(), Instant.now().toString())))
                .onErrorResume(SuperAdminDemoteException.class, e ->
                        ServerResponse.status(HttpStatus.CONFLICT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.CONFLICT.value(), Instant.now().toString())))
                .onErrorResume(AlreadyUserException.class, e ->
                        ServerResponse.status(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new BaseResponse(HttpStatus.BAD_REQUEST.value(), Instant.now().toString())))
                .onErrorResume(Exception.class, e ->
                        ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .contentType(MediaType.TEXT_PLAIN)
                                .bodyValue(HttpStatus.INTERNAL_SERVER_ERROR.value() + ": Unexpected error: " + e.getMessage()));
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
