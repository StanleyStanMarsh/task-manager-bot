package ru.spbstu.hsai.modules.authors.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import ru.spbstu.hsai.modules.authors.dto.Author;

import java.util.List;

@Service
public class AuthorsService {

    private static final Logger log = LoggerFactory.getLogger(AuthorsService.class);

    private final List<Author> authors = List.of(
            new Author(1, "Астафьев Игорь", "example1@mail.ru",
                    List.of("Contribution 1", "Contribution 2")),
            new Author(1, "Ложкина Анастасия", "example2@mail.ru",
                    List.of("Contribution 3", "Contribution 4")),
            new Author(1, "Плужникова Юлия", "example3@mail.ru",
                    List.of("Contribution 5", "Contribution 6"))
    );

    public Flux<Author> getAuthors() {
        log.info("At getting authors");
        return Flux.fromIterable(authors);
    }
}
