package com.hacom.bbnt.service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Safety-net patterns used after the explicit TemplateProfile field map.
 * The field map remains the primary clearing mechanism; these patterns catch
 * unmodelled template-specific values without becoming a source of data.
 */
final class TemplateDataPatterns {
    static final Pattern UNCERTAIN_VALUE = Pattern.compile(
            "(?iu)(?:"
                    + "\\b(?:M|B)\\s*\\d+(?:[.,]\\d+)?\\b"
                    + "|\\bR\\s*\\d+\\b"
                    + "|\\b\\d{2,3}\\s*[x×]\\s*\\d{2,3}(?:\\s*[x×]\\s*\\d{2,3})?\\b"
                    + "|\\b\\d+\\s*(?:tổ|to|mẫu|mau|viên|vien)\\b"
                    + "|mỗi\\s+tổ\\s+\\d+\\s+mẫu"
                    + "|\\b(?:TCVN|QCVN)\\s*[0-9][0-9:./-]*"
                    + ")"
    );
    static final Pattern RECORD_NUMBER = Pattern.compile(
            "(?iu).*/(?:NTCV|YCNT|LM|GM)/[0-9]+[A-Z]?.*"
    );
    static final List<String> ERROR_TOKENS = List.of(
            "#REF!", "#VALUE!", "#NAME?", "#N/A", "#DIV/0!"
    );

    private TemplateDataPatterns() {
    }
}
