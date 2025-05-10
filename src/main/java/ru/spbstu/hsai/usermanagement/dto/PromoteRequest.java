package ru.spbstu.hsai.usermanagement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PromoteRequest {
    @JsonProperty("password")
    private String password;

    public PromoteRequest() {}

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "PromoteRequest{password='" + password + "'}";
    }
}
