package ru.spbstu.hsai.modules.authors.dto;

import java.util.List;

public record Author(int id, String name, String email, List<String> contributions) {}
