package ru.spbstu.hsai.telegram.commands.utils;

import java.time.ZoneId;

public interface FormattedTask {
    String format(ZoneId userZoneId);
}