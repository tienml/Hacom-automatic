package com.hacom.bbnt.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TemplateRegistry(
        Map<MaterialFamily, List<TemplatePair>> candidates,
        Map<MaterialFamily, TemplatePair> recommendedPairs,
        Map<String, TemplateProfile> profilesBySheet,
        List<String> mainTemplates
) {
    public TemplateRegistry {
        EnumMap<MaterialFamily, List<TemplatePair>> candidateCopy = new EnumMap<>(MaterialFamily.class);
        if (candidates != null) {
            candidates.forEach((family, pairs) -> candidateCopy.put(family,
                    pairs == null ? List.of() : List.copyOf(new ArrayList<>(pairs))));
        }
        candidates = Map.copyOf(candidateCopy);

        EnumMap<MaterialFamily, TemplatePair> recommendedCopy = new EnumMap<>(MaterialFamily.class);
        if (recommendedPairs != null) recommendedCopy.putAll(recommendedPairs);
        recommendedPairs = Map.copyOf(recommendedCopy);

        Map<String, TemplateProfile> profilesCopy = new LinkedHashMap<>();
        if (profilesBySheet != null) {
            profilesBySheet.forEach((name, profile) -> profilesCopy.put(normalize(name), profile));
        }
        profilesBySheet = Map.copyOf(profilesCopy);
        mainTemplates = mainTemplates == null ? List.of() : List.copyOf(mainTemplates);
    }

    public TemplatePair pairFor(MaterialFamily family) {
        return recommendedPairs.get(family);
    }

    public List<TemplatePair> pairsFor(MaterialFamily family) {
        return candidates.getOrDefault(family, List.of());
    }

    public TemplateProfile profileFor(String sheetName) {
        return profilesBySheet.get(normalize(sheetName));
    }

    /** Sheet MAIN tốt nhất hiện có để làm layout nguồn khi một DM chưa có sheet chính. */
    public String bestMainTemplate() {
        return mainTemplates.isEmpty() ? null : mainTemplates.get(0);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
