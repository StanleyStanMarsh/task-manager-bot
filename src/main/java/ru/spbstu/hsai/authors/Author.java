package ru.spbstu.hsai.authors;

import java.util.List;

public record Author(int id, String name, String email, List<String> contributions) {}
