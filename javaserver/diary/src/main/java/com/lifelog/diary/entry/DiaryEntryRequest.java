package com.lifelog.diary.entry;

import java.time.LocalDate;
import java.util.List;

public record DiaryEntryRequest(
        String title,
        String content,
        LocalDate entryDate,
        String mood,
        String location,
        List<String> tags,
        List<String> mediaIds
) {}
