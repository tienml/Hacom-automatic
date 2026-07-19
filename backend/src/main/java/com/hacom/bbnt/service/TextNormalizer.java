package com.hacom.bbnt.service;

import java.text.Normalizer;
import java.util.Locale;

final class TextNormalizer {
    private TextNormalizer() {
    }

    static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    static String asciiLower(String value) {
        String normalized = Normalizer.normalize(compact(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return normalized.toLowerCase(Locale.ROOT);
    }
}
