package ru.spbstu.hsai.check;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Controller
public class HealthCheckController {

    private final ReactiveMongoTemplate mongoTemplate;

    @Autowired
    public HealthCheckController(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Mono<ServerResponse> healthCheck(ServerRequest request) {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");

        return mongoTemplate.getCollectionNames()
                .collectList()
                .map(collections -> {
                    status.put("mongo", "UP");
                    status.put("collections", collections.size());
                    return status;
                })
                .onErrorResume(e -> {
                    status.put("mongo", "DOWN");
                    status.put("error", e.getMessage());
                    return Mono.just(status);
                })
                .flatMap(s -> ServerResponse.ok().bodyValue(s));
    }
}
