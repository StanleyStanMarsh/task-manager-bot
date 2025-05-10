package ru.spbstu.hsai.telegram.context.RepeatingTaskCreation;


import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RepeatingTaskCreationContext {
    private final Map<Long, RepeatingTaskCreationState> chatStates = new ConcurrentHashMap<>();

    public void startCreation(Long chatId, String userId) {
        RepeatingTaskCreationState state = new RepeatingTaskCreationState();
        state.setUserId(userId);
        state.setCurrentStep(RepeatingTaskCreationStep.DESCRIPTION);
        chatStates.put(chatId, state);
    }

    public RepeatingTaskCreationState getState(Long chatId) {
        return chatStates.get(chatId);
    }

    public void updateState(Long chatId, RepeatingTaskCreationState newState) {
        chatStates.put(chatId, newState);
    }

    public void complete(Long chatId) {
        chatStates.remove(chatId);
    }

    public boolean hasActiveSession(Long chatId) {
        return chatStates.containsKey(chatId);
    }


}
