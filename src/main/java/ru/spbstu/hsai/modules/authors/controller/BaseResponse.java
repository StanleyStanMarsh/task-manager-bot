package ru.spbstu.hsai.modules.authors.controller;

import ru.spbstu.hsai.modules.authors.dto.Author;

import java.util.List;

public record BaseResponse(int status, List<Author> authors, String timestamp) {}
