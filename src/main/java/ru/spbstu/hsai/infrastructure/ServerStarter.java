package ru.spbstu.hsai.infrastructure;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public interface ServerStarter {
    void start(AnnotationConfigApplicationContext context);
}
