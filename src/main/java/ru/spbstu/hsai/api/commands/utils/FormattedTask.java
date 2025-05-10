package ru.spbstu.hsai.api.commands.utils;

import java.time.ZoneId;

public interface FormattedTask {
    String format(ZoneId userZoneId);
}