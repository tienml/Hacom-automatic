package com.hacom.bbnt.service;

import com.hacom.bbnt.model.DocumentType;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class DescriptionService {
    private static final Pattern QUALITY_PREFIX = Pattern.compile("(?iu)^\\s*chất\\s+lượng\\s+");

    public String normalizeWorkContent(String workContent) {
        String compact = TextNormalizer.compact(workContent);
        return QUALITY_PREFIX.matcher(compact).replaceFirst("").trim();
    }

    public String description(DocumentType type, String workContent, String location) {
        String content = normalizeWorkContent(workContent);
        if (content.isBlank()) return "";
        String prefix = type == DocumentType.LM ? "Lấy mẫu " : "Mẫu ";
        String compactLocation = TextNormalizer.compact(location);
        return prefix + content + (compactLocation.isBlank() ? "" : " (" + compactLocation + ")");
    }
}
