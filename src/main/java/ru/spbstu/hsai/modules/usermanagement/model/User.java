package ru.spbstu.hsai.modules.usermanagement.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed
    private Long telegramId;
    private String username;  // может отсутствовать
    private String firstName;  // может отсутствовать
    private String lastName;  // может отсутствовать
    private String role = "USER"; // значение по умолчанию

    // Конструкторы
    public User() {}

    public User(Long telegramId, String role) {
        this.telegramId = telegramId;
        this.role = role;
    }

    public User() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getTelegramId() {
        return telegramId;
    }
    public void setTelegramId(Long telegramId) {
        this.telegramId = telegramId;
    }

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getRole() {
        return role;
    }
    public void setRole(String role) {
        this.role = role;
    }

    @Override
    public String toString() {
        return "User{id='" + id + "', telegramId=" + telegramId + ", role='" + role + "', username='" + username + "'}";
    }
}


