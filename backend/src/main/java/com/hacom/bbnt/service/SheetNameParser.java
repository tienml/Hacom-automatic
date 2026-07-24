package com.hacom.bbnt.service;

import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.MaterialFamily;
import com.hacom.bbnt.model.ParsedSheetName;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SheetNameParser {
    private static final Pattern MAIN = Pattern.compile("^\\s*([0-9]+[A-Za-z]?)\\s*$");
    private static final Pattern RELATED = Pattern.compile(
            "(?i)(LMV|GMV|LMBT|GMBT)\\s*\\(\\s*([0-9]+[A-Za-z]?)\\s*\\)"
    );

    public Optional<ParsedSheetName> parse(String sheetName) {
        if (sheetName == null) return Optional.empty();
        Matcher mainMatcher = MAIN.matcher(sheetName);
        if (mainMatcher.matches()) {
            return Optional.of(new ParsedSheetName(
                    sheetName,
                    DocumentType.MAIN,
                    MaterialFamily.UNKNOWN,
                    normalizeItemNumber(mainMatcher.group(1)),
                    true
            ));
        }

        Matcher relatedMatcher = RELATED.matcher(sheetName);
        if (!relatedMatcher.find()) return Optional.empty();
        String prefix = relatedMatcher.group(1).toUpperCase(Locale.ROOT);
        DocumentType type = prefix.startsWith("LM") ? DocumentType.LM : DocumentType.GM;
        MaterialFamily family = prefix.endsWith("BT") ? MaterialFamily.BETONG : MaterialFamily.VUA;
        return Optional.of(new ParsedSheetName(
                sheetName,
                type,
                family,
                normalizeItemNumber(relatedMatcher.group(2)),
                false
        ));
    }

    public String plannedSheetName(DocumentType type, MaterialFamily family, String itemNumber) {
        if (type == DocumentType.MAIN) return normalizeItemNumber(itemNumber);
        String prefix = switch (type) {
            case LM -> family == MaterialFamily.BETONG ? "1.LMBT" : "1.LMV";
            case GM -> family == MaterialFamily.BETONG ? "1.GMBT" : "1.GMV";
            default -> throw new IllegalArgumentException("Chỉ hỗ trợ tên sheet MAIN/LM/GM.");
        };
        return prefix + " (" + normalizeItemNumber(itemNumber) + ")";
    }

    public String normalizeItemNumber(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
