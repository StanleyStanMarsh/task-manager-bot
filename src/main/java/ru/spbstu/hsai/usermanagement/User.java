package ru.spbstu.hsai.usermanagement;

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
    private String password;
    private String timezone; // новое поле для часового пояса

    // Конструкторы
    public User() {}

    public User(Long telegramId, String role) {
        this.telegramId = telegramId;
        this.role = role;
    }

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

    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }

    //для напоминаний
    public String getTimezone() {
        return timezone;
    }
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    @Override
    public String toString() {
        return "User{id='" + id + "', telegramId=" + telegramId + ", role='" + role + "', username='" + username + "', timezone='" + timezone + "'}";
    }
}


