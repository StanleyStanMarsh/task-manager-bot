package ru.spbstu.hsai.api.context.simpleTaskUpdate;


import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SimpleTaskUpdateContext {
    private final Map<Long, SimpleTaskUpdateState> updateStates = new HashMap<>();

    public void startUpdate(Long chatId, String taskId, String userId) {
        SimpleTaskUpdateState state = new SimpleTaskUpdateState(taskId, userId);
        updateStates.put(chatId, state);
    }

    public boolean hasActiveSession(Long chatId) {
        return updateStates.containsKey(chatId);
    }

    public SimpleTaskUpdateState getState(Long chatId) {
        return updateStates.get(chatId);
    }

    public void updateState(Long chatId, SimpleTaskUpdateState state) {
        updateStates.put(chatId, state);
    }

    public void complete(Long chatId) {
        updateStates.remove(chatId);
    }

}
