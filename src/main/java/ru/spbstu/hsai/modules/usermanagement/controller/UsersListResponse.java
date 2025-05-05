package ru.spbstu.hsai.modules.usermanagement.controller;

import ru.spbstu.hsai.modules.usermanagement.dto.FormattedUser;

import java.util.List;

public record UsersListResponse(int status, List<FormattedUser> users, long count, String timestamp) {}
