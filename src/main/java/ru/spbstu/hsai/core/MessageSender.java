package ru.spbstu.hsai.core;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

public interface MessageSender {
    CompletableFuture<Message> sendAsync(SendMessage message);
    Message send(SendMessage message);
    Mono<Message> sendReactive(SendMessage message);
}
