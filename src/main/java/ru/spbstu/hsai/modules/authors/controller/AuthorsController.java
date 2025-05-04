package ru.spbstu.hsai.modules.authors.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.authors.dto.Author;
import ru.spbstu.hsai.modules.authors.service.AuthorsService;

import java.time.Instant;

@Controller
public class AuthorsController {

    private static final Logger log = LoggerFactory.getLogger(AuthorsController.class);

    private final AuthorsService authorsService;

    public AuthorsController(AuthorsService authorsService) {
        this.authorsService = authorsService;
    }

    public Mono<ServerResponse> authors(ServerRequest request) {
        return authorsService.getAuthors()
                .collectList() // Преобразуем Flux<Author> -> Mono<List<Author>>
                .flatMap(authorsList -> {
                    BaseResponse response = new BaseResponse(200, authorsList, Instant.now().toString());
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(response);
                })
                .doOnSuccess(response -> log.info("Got authors response"))
                .doOnError(error -> log.error("Got error: {}", error.getMessage()));
    }
}