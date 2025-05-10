package ru.spbstu.hsai.telegram.context.RepeatingTaskCreation;


import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTask;
import java.time.LocalDateTime;


public class RepeatingTaskCreationState {
    private String userId;
    private String description;
    private Integer complexity;
    private RepeatingTask.RepeatFrequency frequency;
    private LocalDateTime startDateTime;
    private RepeatingTaskCreationStep currentStep = RepeatingTaskCreationStep.START;

    // Геттеры и сеттеры
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getComplexity() { return complexity; }
    public void setComplexity(Integer complexity) { this.complexity = complexity; }

    public RepeatingTask.RepeatFrequency getFrequency() { return frequency; }
    public void setFrequency( RepeatingTask.RepeatFrequency frequency) { this.frequency = frequency; }

    public LocalDateTime getStartDateTime() { return startDateTime; };
    public void setStartDateTime(LocalDateTime startDateTime) { this.startDateTime = startDateTime; }

    public RepeatingTaskCreationStep getCurrentStep() { return currentStep; }
    public void setCurrentStep(RepeatingTaskCreationStep currentStep) { this.currentStep = currentStep; }

}
