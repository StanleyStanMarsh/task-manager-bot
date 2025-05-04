package ru.spbstu.hsai.api.context.simpleTaskCreation;

import ru.spbstu.hsai.modules.simpletaskmanagment.model.SimpleTask;
import java.time.LocalDate;


public class SimpleTaskCreationState {
    private String userId;
    private String description;
    private Integer complexity;
    private LocalDate deadline;
    private SimpleTask.ReminderType reminder;
    private SimpleTaskCreationStep currentStep = SimpleTaskCreationStep.START;

    // Геттеры и сеттеры
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getComplexity() { return complexity; }
    public void setComplexity(Integer complexity) { this.complexity = complexity; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

    public SimpleTask.ReminderType getReminder() { return reminder; }
    public void setReminder(SimpleTask.ReminderType reminder) { this.reminder = reminder; }

    public SimpleTaskCreationStep getCurrentStep() { return currentStep; }
    public void setCurrentStep(SimpleTaskCreationStep currentStep) { this.currentStep = currentStep; }


}
