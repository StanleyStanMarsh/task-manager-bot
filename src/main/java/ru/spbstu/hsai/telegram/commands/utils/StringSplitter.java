package ru.spbstu.hsai.telegram.commands.utils;

import java.util.ArrayList;
import java.util.List;

public class StringSplitter {
    public static List<String> splitToChunks(String text, int maxLength) {
        List<String> chunks = new ArrayList<>();
        int start = 0, len = text.length();
        while (start < len) {
            int end = Math.min(start + maxLength, len);
            if (end < len) {
                // ищем последнюю \n между start и end
                int nl = text.lastIndexOf('\n', end);
                if (nl > start) {
                    end = nl + 1;  // включаем сам перенос
                }
            }
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }
}
