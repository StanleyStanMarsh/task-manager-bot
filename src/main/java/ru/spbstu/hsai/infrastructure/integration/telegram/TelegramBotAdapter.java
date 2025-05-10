package ru.spbstu.hsai.infrastructure.integration.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import ru.spbstu.hsai.infrastructure.BotProperties;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
public class TelegramBotAdapter extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotAdapter.class);
    String token;
    String username;
    private final UpdateReceiveService updateService;

    public TelegramBotAdapter(BotProperties botProperties, UpdateReceiveService updateService) {
        this.token = botProperties.token();
        this.username = botProperties.botUsername();
        this.updateService = updateService;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        updateService.updateHandle(update);
    }

    @PostConstruct
    public void registerBotCommands() {
        List<BotCommand> commands = List.of(
                new BotCommand("/start", "запустить бота"),
                new BotCommand("/help", "показать справку"),
                new BotCommand("/newtask", "создать задачу"),
                new BotCommand("/newrepeatingtask", "создать периодическую задачу"),
                new BotCommand("/updatetask", "редактировать задачу по <ID>"),
                new BotCommand("/deletetask", "удалить задачу по <ID>"),
                new BotCommand("/mytasks", "просмотр всех активных задач"),
                new BotCommand("/completed", "просмотр завершенных задач")
        );

        SetMyCommands setMyCommands = new SetMyCommands();
        setMyCommands.setCommands(commands);
        setMyCommands.setScope(new BotCommandScopeDefault());

        try {
            execute(setMyCommands);
        } catch (Exception e) {
            log.warn("Error while setting commands", e);
        }
    }
}
