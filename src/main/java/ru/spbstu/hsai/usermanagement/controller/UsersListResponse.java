package ru.spbstu.hsai.usermanagement.controller;

import ru.spbstu.hsai.usermanagement.dto.FormattedUser;

import java.util.List;

public record UsersListResponse(int status, List<FormattedUser> users, long count, String timestamp) {}
