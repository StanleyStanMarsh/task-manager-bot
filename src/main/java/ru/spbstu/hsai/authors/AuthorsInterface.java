package ru.spbstu.hsai.authors;

import reactor.core.publisher.Flux;

public interface AuthorsInterface {
    Flux<Author> getAuthors();
}
