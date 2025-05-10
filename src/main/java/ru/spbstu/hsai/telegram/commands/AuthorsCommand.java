package ru.spbstu.hsai.telegram.commands;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.telegram.events.UpdateReceivedEvent;
import ru.spbstu.hsai.telegram.MessageSender;
import ru.spbstu.hsai.authors.AuthorsInterface;

@Component
public class AuthorsCommand implements TelegramCommand {

    private final MessageSender sender;
    private final AuthorsInterface authorsService;

    public AuthorsCommand(MessageSender sender,
                          AuthorsInterface authorsService) {
        this.sender = sender;
        this.authorsService = authorsService;
    }

    @Override
    public boolean supports(String command) {
        return "/authors".equalsIgnoreCase(command);
    }

    @EventListener(condition = "@telegramCommandsExtract.checkCommand(#a0.update, '/authors')")
    public void handle(UpdateReceivedEvent event) {
        var message = event.getUpdate().getMessage();
        var chatId  = message.getChatId().toString();

        // 1. Получаем поток авторов из сервиса
        Flux<String> lines = authorsService.getAuthors()
                .map(author -> String.format(
                        "-- %s",
                        author.name()
                ));

        // 2. Собираем всё в один текст
        Mono<String> textMono = lines
                .collectList()
                .map(list -> {
                    if (list.isEmpty()) {
                        return "Список авторов пуст.";
                    }
                    return "\uD83D\uDC68\u200D\uD83D\uDCBBЭтот бот был разработан командой:\n"
                            + String.join("\n", list)
                            + "\n\uD83D\uDD27Проект создан в рамках учебной дисциплины <<Программирование на языке JAVA>>\n"
                            + "✨Спасибо, что пользуетесь нашим Ботом!";
                });

        // 3. Отправляем сообщение
        textMono
                .flatMap(text -> Mono.just(
                        SendMessage.builder()
                                .chatId(chatId)
                                .text(text)
                                .build()
                ))
                .flatMap(sm -> Mono.fromCompletionStage(sender.sendAsync(sm)))
                .doOnError(err -> System.err.println("Ошибка при отправке списка авторов: " + err.getMessage()))
                .subscribe();
    }
}
