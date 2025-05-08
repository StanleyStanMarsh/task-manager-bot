package ru.spbstu.hsai.api.context.simpleTaskUpdate;

import ru.spbstu.hsai.modules.simpletaskmanagment.model.SimpleTask;

import java.time.LocalDate;

public class SimpleTaskUpdateState {
    private String taskId;
    private String userId;
    private String newDescription;
    private Integer newComplexity;
    private LocalDate newDeadline;
    private SimpleTask.ReminderType newReminder;
    private Boolean markAsCompleted = false;
    private SimpleTaskUpdateStep currentStep = SimpleTaskUpdateStep.START;

    public SimpleTaskUpdateState(String taskId, String userId) {
        this.taskId = taskId;
        this.userId = userId;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = userId; }

    public String getDescription() { return newDescription; }
    public void setDescription(String description) { this.newDescription = description; }

    public Integer getComplexity() { return newComplexity; }
    public void setComplexity(Integer complexity) { this.newComplexity = complexity; }

    public LocalDate getDeadline() { return newDeadline; }
    public void setDeadline(LocalDate deadline) { this.newDeadline = deadline; }

    public SimpleTask.ReminderType getReminder() { return newReminder; }
    public void setReminder(SimpleTask.ReminderType reminder) { this.newReminder = reminder; }

    public SimpleTaskUpdateStep getCurrentStep() { return currentStep; }
    public void setCurrentStep(SimpleTaskUpdateStep currentStep) { this.currentStep = currentStep; }

    public Boolean getMarkAsCompleted() { return markAsCompleted; }
    public void setMarkAsCompleted(Boolean markAsCompleted) { this.markAsCompleted = markAsCompleted; }

}
