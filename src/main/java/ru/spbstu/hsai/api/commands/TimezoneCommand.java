package ru.spbstu.hsai.api.commands;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.api.events.UpdateReceivedEvent;
import ru.spbstu.hsai.infrastructure.integration.telegram.TelegramSenderService;
import ru.spbstu.hsai.modules.usermanagement.service.UserService;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TimezoneCommand implements TelegramCommand {

    private final TelegramSenderService sender;
    private final UserService userService;
    private final ConcurrentHashMap<Long, Boolean> awaitingTimezoneUsers;
    private static final Map<String, String> TIMEZONE_MAPPING = Map.ofEntries(
            Map.entry("МСК-1", "Europe/Kaliningrad"),
            Map.entry("МСК", "Europe/Moscow"),
            Map.entry("МСК+1", "Europe/Samara"),
            Map.entry("МСК+2", "Asia/Yekaterinburg"),
            Map.entry("МСК+3", "Asia/Omsk"),
            Map.entry("МСК+4", "Asia/Krasnoyarsk"),
            Map.entry("МСК+5", "Asia/Irkutsk"),
            Map.entry("МСК+6", "Asia/Yakutsk"),
            Map.entry("МСК+7", "Asia/Vladivostok"),
            Map.entry("МСК+8", "Asia/Magadan"),
            Map.entry("МСК+9", "Asia/Kamchatka")
    );

    @Autowired
    public TimezoneCommand(TelegramSenderService sender, UserService userService) {
        this.sender = sender;
        this.userService = userService;
        this.awaitingTimezoneUsers = new ConcurrentHashMap<>();
    }

    @Override
    public boolean supports(String command) {
        return false; // Этот компонент не обрабатывает команды, только текстовые сообщения
    }

    @EventListener
    public void handle(UpdateReceivedEvent event) {
        if (!event.getUpdate().hasMessage() || !event.getUpdate().getMessage().hasText()) {
            return;
        }

        Message message = event.getUpdate().getMessage();
        User tgUser = message.getFrom();
        Long chatId = tgUser.getId();
        String text = message.getText().trim();

        // Проверяем, ожидает ли пользователь ввода часового пояса
        if (!awaitingTimezoneUsers.containsKey(chatId)) {
            return;
        }

        // Проверяем валидность часового пояса
        if (TIMEZONE_MAPPING.containsKey(text)) {
            String zoneId = TIMEZONE_MAPPING.get(text);
            try {
                ZoneId.of(zoneId); // Проверяем, что ZoneId валидный
                // Сохраняем часовой пояс
                userService.updateTimezone(chatId, zoneId)
                        .then(Mono.just(SendMessage.builder()
                                .chatId(chatId)
                                .text(String.format(
                                        """
                                        🎉 Добро пожаловать, %s! 🎉
                                        Ваш часовой пояс (%s) успешно сохранен!
                                        Этот Бот поможет тебе управлять задачами и не забывать о них!
                                        Используйте /help, чтобы ознакомиться со списком команд.
                                        """,
                                        tgUser.getUserName() != null ? tgUser.getUserName() : tgUser.getFirstName(),
                                        text
                                ))
                                .build()))
                        .flatMap(sm -> Mono.fromCompletionStage(sender.sendAsync(sm)))
                        .doOnSuccess(v -> {
                            awaitingTimezoneUsers.remove(chatId);
                            System.out.println("Timezone set and welcome message sent for chatId=" + chatId);
                        })
                        .doOnError(e -> System.out.println("Error saving timezone or sending welcome: " + e.getMessage()))
                        .subscribe();
            } catch (Exception e) {
                sendInvalidTimezonePrompt(chatId);
            }
        } else {
            sendInvalidTimezonePrompt(chatId);
        }
    }

    private void sendInvalidTimezonePrompt(Long chatId) {
        Mono.just(SendMessage.builder()
                        .chatId(chatId)
                        .text("""
                      Неверный часовой пояс. Пожалуйста, выберите из списка ниже и отправьте его название (например, МСК):
                      - МСК-1 (калининградское время)
                      - МСК   (московское время)
                      - МСК+1 (самарское время)
                      - МСК+2 (екатеринбургское время)
                      - МСК+3 (омское время)
                      - МСК+4 (красноярское время)
                      - МСК+5 (иркутское время)
                      - МСК+6 (якутское время)
                      - МСК+7 (владивостокское время)
                      - МСК+8 (магаданское время)
                      - МСК+9 (камчатское время)
                      """)
                        .build())
                .flatMap(sm -> Mono.fromCompletionStage(sender.sendAsync(sm)))
                .doOnSuccess(v -> System.out.println("Invalid timezone, prompt resent for chatId=" + chatId))
                .doOnError(e -> System.out.println("Error sending timezone prompt: " + e.getMessage()))
                .subscribe();
    }

    // Метод для добавления пользователя в список ожидающих часовой пояс
    public void addAwaitingTimezoneUser(Long chatId) {
        awaitingTimezoneUsers.put(chatId, true);
    }
}