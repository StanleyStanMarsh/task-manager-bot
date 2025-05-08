package ru.spbstu.hsai.api.commands;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.api.events.UpdateReceivedEvent;
import ru.spbstu.hsai.infrastructure.integration.telegram.TelegramSenderService;

@Component
public class HelpCommand implements TelegramCommand {

    private final TelegramSenderService sender;

    public HelpCommand(TelegramSenderService sender) {
        this.sender = sender;
    }

    @Override
    public boolean supports(String command) {
        return "/help".equalsIgnoreCase(command);
    }

    @EventListener(condition = "@telegramCommandsExtract.checkCommand(#a0.update, '/help')")
    public void handle(UpdateReceivedEvent event) {
        Message message = event.getUpdate().getMessage();
        User user = message.getFrom();

        // Создаем реактивный SendMessage
        Mono<SendMessage> sendMessageMono = Mono.just(SendMessage.builder()
                .chatId(user.getId())
                .text(
                        """
                        📋Список команд:
                        🎯*Управление задачами*
                        /newtask - создать задачу;
                        /updatetask <ID> - редактировать задачу;
                        /deletetask <ID> - удалить задачу;
                        /newrepeatingtask - создать периодическую задачу;
                        😱*Просмотр задач*
                        /mytasks - просмотр всех активных задач;
                        /today - просмотр задач на сегодня;
                        /week - просмотр задач на текущую неделю;
                        /date <дд.мм.гггг> - просмотр задач на указанную дату;
                        /completed - просмотр завершенных задач;
                        💡*Информация*
                        /status - показать статус Бота;
                        /authors - об авторах
                        """
                )
                .build());

        // Реактивно отправляем сообщение
        sendMessageMono
                .flatMap(sm -> Mono.fromCompletionStage(sender.sendAsync(sm)))
                .subscribe(); // Fire-and-forget
    }
}