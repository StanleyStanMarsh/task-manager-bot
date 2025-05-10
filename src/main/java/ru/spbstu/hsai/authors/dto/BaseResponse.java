package ru.spbstu.hsai.authors.dto;

import ru.spbstu.hsai.authors.Author;

import java.util.List;

public record BaseResponse(int status, List<Author> authors, String timestamp) {}
