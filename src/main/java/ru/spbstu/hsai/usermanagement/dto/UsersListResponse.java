package ru.spbstu.hsai.usermanagement.dto;

import java.util.List;

public record UsersListResponse(int status, List<FormattedUser> users, long count, String timestamp) {}
