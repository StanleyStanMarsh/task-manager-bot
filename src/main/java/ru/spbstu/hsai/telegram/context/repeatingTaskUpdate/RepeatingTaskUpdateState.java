package ru.spbstu.hsai.telegram.context.repeatingTaskUpdate;

import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTask;

import java.time.LocalDateTime;

public class RepeatingTaskUpdateState {
    private String taskId;
    private String userId;
    private String newDescription;
    private Integer newComplexity;
    private RepeatingTask.RepeatFrequency newFrequency;
    private LocalDateTime newStartDateTime;
    private RepeatingTaskUpdateStep currentStep = RepeatingTaskUpdateStep.START;

    public RepeatingTaskUpdateState(String taskId, String userId) {
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

    public RepeatingTask.RepeatFrequency getFrequency() { return newFrequency; }
    public void setFrequency( RepeatingTask.RepeatFrequency frequency) { this.newFrequency = frequency; }

    public LocalDateTime getStartDateTime() { return newStartDateTime; };
    public void setStartDateTime(LocalDateTime startDateTime) { this.newStartDateTime = startDateTime; }

    public RepeatingTaskUpdateStep getCurrentStep() { return currentStep; }
    public void setCurrentStep(RepeatingTaskUpdateStep currentStep) { this.currentStep = currentStep; }

}
