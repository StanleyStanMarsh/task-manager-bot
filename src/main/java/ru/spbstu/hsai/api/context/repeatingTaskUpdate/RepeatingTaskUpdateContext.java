package ru.spbstu.hsai.api.context.repeatingTaskUpdate;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RepeatingTaskUpdateContext {
    private final Map<Long, RepeatingTaskUpdateState> updateStates = new ConcurrentHashMap<>();

    public void startUpdate(Long chatId, String taskId, String userId) {
        RepeatingTaskUpdateState state = new RepeatingTaskUpdateState(taskId, userId);
        updateStates.put(chatId, state);
    }

    public boolean hasActiveSession(Long chatId) {
        return updateStates.containsKey(chatId);
    }

    public  RepeatingTaskUpdateState getState(Long chatId) {
        return updateStates.get(chatId);
    }

    public void updateState(Long chatId,  RepeatingTaskUpdateState state) {
        updateStates.put(chatId, state);
    }

    public void complete(Long chatId) {
        updateStates.remove(chatId);
    }

}
