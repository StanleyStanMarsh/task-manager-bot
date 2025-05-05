package ru.spbstu.hsai.api.context.simpleTaskCreation;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimpleTaskCreationContext {
    private final Map<Long, SimpleTaskCreationState> chatStates = new ConcurrentHashMap<>();

    public void startCreation(Long chatId, String userId) {
        SimpleTaskCreationState state = new SimpleTaskCreationState();
        state.setUserId(userId);
        state.setCurrentStep(SimpleTaskCreationStep.DESCRIPTION);
        chatStates.put(chatId, state);
    }

    public SimpleTaskCreationState getState(Long chatId) {
        return chatStates.get(chatId);
    }

    public void updateState(Long chatId, SimpleTaskCreationState newState) {
        chatStates.put(chatId, newState);
    }

    public void complete(Long chatId) {
        chatStates.remove(chatId);
    }

    public boolean hasActiveSession(Long chatId) {
        return chatStates.containsKey(chatId);
    }

}
