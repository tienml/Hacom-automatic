package com.hacom.bbnt.service;

import com.hacom.bbnt.model.DocumentType;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Service
public class DocumentNumberService {
    private static final Set<String> DOCUMENT_SEGMENTS = Set.of("NTCV", "YCNT", "LM", "GM");

    public String convert(String source, DocumentType targetType) {
        if (source == null || source.isBlank()) return "";
        if (targetType != DocumentType.LM && targetType != DocumentType.GM) return "";
        String[] segments = Arrays.stream(source.trim().split("/", -1))
                .map(String::trim)
                .toArray(String[]::new);
        if (segments.length < 3) return "";
        int index = -1;
        for (int i = 0; i < segments.length; i++) {
            if (DOCUMENT_SEGMENTS.contains(segments[i].toUpperCase(Locale.ROOT))) {
                index = i;
                break;
            }
        }
        if (index < 0) return "";
        segments[index] = targetType.name();
        return String.join("/", segments);
    }
}
