package com.hacom.bbnt.service;

import com.hacom.bbnt.dto.DocumentPlanDto;
import com.hacom.bbnt.dto.FieldDecisionDto;
import com.hacom.bbnt.dto.ProjectSummary;
import com.hacom.bbnt.dto.TemplatePairDto;
import com.hacom.bbnt.dto.WorkItemDto;
import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.FieldAction;
import com.hacom.bbnt.model.GenerationMode;
import com.hacom.bbnt.model.MaterialFamily;
import com.hacom.bbnt.model.OutputAvailability;
import com.hacom.bbnt.model.ParsedSheetName;
import com.hacom.bbnt.model.TemplatePair;
import com.hacom.bbnt.model.TemplateProfile;
import com.hacom.bbnt.model.TemplateRegistry;
import com.hacom.bbnt.model.WorkItemSheetStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class WorkItemPlanningService {
    private final MaterialClassificationService materialClassificationService;
    private final SheetNameParser sheetNameParser;
    private final FieldDecisionService fieldDecisionService;

    public WorkItemPlanningService(
            MaterialClassificationService materialClassificationService,
            SheetNameParser sheetNameParser,
            FieldDecisionService fieldDecisionService
    ) {
        this.materialClassificationService = materialClassificationService;
        this.sheetNameParser = sheetNameParser;
        this.fieldDecisionService = fieldDecisionService;
    }

    public WorkItemDto plan(
            String itemNumber,
            String localOrder,
            String content,
            String position,
            String inspectionTime,
            String recordNumber,
            String sampleDate,
            int excelRow,
            List<String> existingSheetNames,
            TemplateRegistry registry,
            ProjectSummary project,
            List<String> analysisWarnings
    ) {
        List<String> existing = existingSheetNames == null ? List.of() : List.copyOf(existingSheetNames);
        ExistingByType existingByType = classifyExisting(existing);
        MaterialClassificationService.Classification classification = materialClassificationService.classify(content);
        List<String> planningWarnings = new ArrayList<>();
        if (analysisWarnings != null) planningWarnings.addAll(analysisWarnings);
        MaterialFamily family = resolveMaterialFamily(classification.materialFamily(), existingByType, planningWarnings);
        TemplatePair recommendedPair = family == MaterialFamily.UNKNOWN ? null : registry.pairFor(family);

        WorkItemDto shell = shell(
                itemNumber, localOrder, content, position, inspectionTime, recordNumber, sampleDate, excelRow,
                existing, existingByType, classification, family, recommendedPair, registry, planningWarnings
        );
        DocumentPlanDto mainPlan = mainPlan(existingByType.main, family);
        DocumentPlanDto lmPlan = samplePlan(shell, DocumentType.LM, existingByType.lm, family, recommendedPair, registry, project);
        DocumentPlanDto gmPlan = samplePlan(shell, DocumentType.GM, existingByType.gm, family, recommendedPair, registry, project);

        boolean missingSampleDocument = !existingByType.hasLm() || !existingByType.hasGm();
        boolean requiresTemplateSelection = missingSampleDocument
                && (family == MaterialFamily.UNKNOWN || recommendedPair == null || !recommendedPair.usable());
        GenerationMode aggregateMode = missingSampleDocument && !requiresTemplateSelection
                ? GenerationMode.CLONE_TEMPLATE
                : GenerationMode.EXISTING_SHEET;
        WorkItemSheetStatus status = status(existingByType, family);

        List<FieldDecisionDto> decisions = new ArrayList<>();
        if (lmPlan.fieldDecisions() != null) decisions.addAll(lmPlan.fieldDecisions());
        if (gmPlan.fieldDecisions() != null) decisions.addAll(gmPlan.fieldDecisions());
        List<String> autoFilled = decisions.stream()
                .filter(decision -> decision.action() == FieldAction.POPULATE)
                .map(FieldDecisionDto::fieldName)
                .distinct()
                .toList();
        List<String> blankFields = decisions.stream()
                .filter(decision -> decision.action() == FieldAction.CLEAR)
                .map(FieldDecisionDto::fieldName)
                .distinct()
                .toList();
        Set<String> warnings = new LinkedHashSet<>();
        warnings.addAll(planningWarnings);
        warnings.addAll(lmPlan.warnings());
        warnings.addAll(gmPlan.warnings());
        if (existingByType.hasMain() && !existingByType.hasLm() && !existingByType.hasGm()) {
            warnings.add("Item chỉ có MAIN; LM và GM được lập kế hoạch độc lập để có thể clone từ template.");
        }

        return new WorkItemDto(
                itemNumber,
                localOrder,
                content,
                position,
                inspectionTime,
                recordNumber,
                blankToNull(sampleDate),
                excelRow,
                !existing.isEmpty(),
                existing,
                existingByType.hasMain(),
                existingByType.hasLm(),
                existingByType.hasGm(),
                existingByType.hasLm() && existingByType.hasGm(),
                existingByType.hasLm() ^ existingByType.hasGm(),
                status,
                aggregateMode,
                family,
                classification.reason(),
                pairDto(recommendedPair, true),
                registry.pairsFor(family).stream().map(pair -> pairDto(pair, samePair(pair, recommendedPair))).toList(),
                requiresTemplateSelection,
                mainPlan,
                lmPlan,
                gmPlan,
                decisions,
                autoFilled,
                blankFields,
                List.copyOf(warnings)
        );
    }

    private WorkItemDto shell(
            String itemNumber,
            String localOrder,
            String content,
            String position,
            String inspectionTime,
            String recordNumber,
            String sampleDate,
            int excelRow,
            List<String> existing,
            ExistingByType byType,
            MaterialClassificationService.Classification classification,
            MaterialFamily effectiveFamily,
            TemplatePair pair,
            TemplateRegistry registry,
            List<String> analysisWarnings
    ) {
        return new WorkItemDto(
                itemNumber, localOrder, content, position, inspectionTime, recordNumber, blankToNull(sampleDate), excelRow,
                !existing.isEmpty(), existing, byType.hasMain(), byType.hasLm(), byType.hasGm(),
                byType.hasLm() && byType.hasGm(), byType.hasLm() ^ byType.hasGm(),
                status(byType, effectiveFamily), GenerationMode.EXISTING_SHEET,
                effectiveFamily, detectionReason(classification, byType, effectiveFamily), pairDto(pair, true),
                registry.pairsFor(effectiveFamily).stream().map(value -> pairDto(value, samePair(value, pair))).toList(),
                false, null, null, null, List.of(), List.of(), List.of(), analysisWarnings
        );
    }

    private DocumentPlanDto mainPlan(String existingSheet, MaterialFamily family) {
        if (existingSheet == null) {
            return new DocumentPlanDto(DocumentType.MAIN, OutputAvailability.NOT_APPLICABLE, null,
                    null, null, family, null, List.of(), List.of(),
                    List.of("Ứng dụng không tự sinh sheet MAIN khi workbook chưa có."));
        }
        return new DocumentPlanDto(DocumentType.MAIN, OutputAvailability.EXISTING, GenerationMode.EXISTING_SHEET,
                existingSheet, existingSheet, family, null, List.of(), List.of(), List.of());
    }

    private DocumentPlanDto samplePlan(
            WorkItemDto item,
            DocumentType type,
            String existingSheet,
            MaterialFamily family,
            TemplatePair recommendedPair,
            TemplateRegistry registry,
            ProjectSummary project
    ) {
        if (existingSheet != null) {
            return new DocumentPlanDto(type, OutputAvailability.EXISTING, GenerationMode.EXISTING_SHEET,
                    existingSheet, existingSheet, family, null, availableTemplates(registry, family, type),
                    List.of(), List.of("Sử dụng sheet hiện có; không sanitize dữ liệu người dùng."));
        }
        if (family == MaterialFamily.UNKNOWN) {
            return new DocumentPlanDto(type, OutputAvailability.MISSING_TEMPLATE, null,
                    null, null, family, null, List.of(), List.of(),
                    List.of("Chưa xác định vật liệu; người dùng phải chọn Vữa hoặc Bê tông."));
        }
        String plannedName = sheetNameParser.plannedSheetName(type, family, item.itemNumber());
        String sourceTemplate = recommendedPair == null
                ? null
                : type == DocumentType.LM ? recommendedPair.lmSheetName() : recommendedPair.gmSheetName();
        TemplateProfile profile = sourceTemplate == null ? null : registry.profileFor(sourceTemplate);
        if (recommendedPair == null || !recommendedPair.usable() || profile == null) {
            return new DocumentPlanDto(type, OutputAvailability.MISSING_TEMPLATE, null,
                    null, plannedName, family, sourceTemplate, availableTemplates(registry, family, type),
                    List.of(), List.of("Không có template " + type + " tương thích profile cho " + family + "."));
        }
        List<FieldDecisionDto> decisions = fieldDecisionService.decisions(item, project, profile, type);
        return new DocumentPlanDto(type, OutputAvailability.GENERATABLE, GenerationMode.CLONE_TEMPLATE,
                null, plannedName, family, sourceTemplate, availableTemplates(registry, family, type),
                decisions, recommendedPair.validationWarnings());
    }

    private ExistingByType classifyExisting(List<String> names) {
        String main = null;
        String lm = null;
        String gm = null;
        MaterialFamily lmFamily = MaterialFamily.UNKNOWN;
        MaterialFamily gmFamily = MaterialFamily.UNKNOWN;
        for (String name : names) {
            ParsedSheetName parsed = sheetNameParser.parse(name).orElse(null);
            if (parsed == null) continue;
            if (parsed.mainSheet() && main == null) main = name;
            else if (parsed.documentType() == DocumentType.LM && lm == null) {
                lm = name;
                lmFamily = parsed.materialFamily();
            } else if (parsed.documentType() == DocumentType.GM && gm == null) {
                gm = name;
                gmFamily = parsed.materialFamily();
            }
        }
        return new ExistingByType(main, lm, gm, lmFamily, gmFamily);
    }

    private MaterialFamily resolveMaterialFamily(
            MaterialFamily classified,
            ExistingByType existing,
            List<String> warnings
    ) {
        MaterialFamily existingFamily = existing.sampleFamily();
        if (existing.familyConflict()) {
            warnings.add("LM và GM hiện có thuộc hai họ vật liệu khác nhau; không tự suy đoán family cho sheet còn thiếu.");
            return MaterialFamily.UNKNOWN;
        }
        if (existingFamily == MaterialFamily.UNKNOWN) return classified;
        if (classified != MaterialFamily.UNKNOWN && classified != existingFamily) {
            warnings.add("Loại vật liệu nhận diện từ nội dung (" + classified
                    + ") khác sheet mẫu hiện có (" + existingFamily
                    + "); ưu tiên family của LM/GM hiện có để không tạo cặp chéo.");
        } else if (classified == MaterialFamily.UNKNOWN) {
            warnings.add("Loại vật liệu được xác định từ sheet LM/GM hiện có: " + existingFamily + ".");
        }
        return existingFamily;
    }

    private String detectionReason(
            MaterialClassificationService.Classification classification,
            ExistingByType existing,
            MaterialFamily effectiveFamily
    ) {
        MaterialFamily existingFamily = existing.sampleFamily();
        if (existingFamily != MaterialFamily.UNKNOWN && !existing.familyConflict()) {
            return "Nhận diện từ sheet LM/GM hiện có (" + existingFamily + ")"
                    + (classification.materialFamily() == existingFamily
                    ? "; nội dung DM đồng nhất."
                    : "; nội dung DM không được dùng để ghi đè family của hồ sơ hiện có.");
        }
        if (effectiveFamily == MaterialFamily.UNKNOWN && existing.familyConflict()) {
            return "LM và GM hiện có không đồng nhất family.";
        }
        return classification.reason();
    }

    private WorkItemSheetStatus status(ExistingByType byType, MaterialFamily family) {
        if (family == MaterialFamily.UNKNOWN && (!byType.hasLm() || !byType.hasGm())) {
            return WorkItemSheetStatus.UNKNOWN_MATERIAL;
        }
        if (byType.hasLm() && byType.hasGm()) return WorkItemSheetStatus.COMPLETE_SAMPLE_PAIR;
        if (byType.hasLm()) return WorkItemSheetStatus.MISSING_GM;
        if (byType.hasGm()) return WorkItemSheetStatus.MISSING_LM;
        if (byType.hasMain()) return WorkItemSheetStatus.MAIN_ONLY;
        return WorkItemSheetStatus.NO_SHEETS;
    }

    private List<String> availableTemplates(TemplateRegistry registry, MaterialFamily family, DocumentType type) {
        if (family == MaterialFamily.UNKNOWN) return List.of();
        return registry.pairsFor(family).stream()
                .filter(TemplatePair::usable)
                .map(pair -> type == DocumentType.LM ? pair.lmSheetName() : pair.gmSheetName())
                .distinct()
                .toList();
    }

    private TemplatePairDto pairDto(TemplatePair pair, boolean recommended) {
        return pair == null ? null : new TemplatePairDto(
                pair.lmSheetName(), pair.gmSheetName(), pair.reason(), recommended,
                pair.profileCompatible(), pair.mergedRegionCount(), pair.drawingCount(),
                pair.hasPrintArea(), pair.validationWarnings()
        );
    }


    private boolean samePair(TemplatePair left, TemplatePair right) {
        return left != null && right != null
                && left.lmSheetName().equalsIgnoreCase(right.lmSheetName())
                && left.gmSheetName().equalsIgnoreCase(right.gmSheetName());
    }
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record ExistingByType(
            String main,
            String lm,
            String gm,
            MaterialFamily lmFamily,
            MaterialFamily gmFamily
    ) {
        boolean hasMain() { return main != null; }
        boolean hasLm() { return lm != null; }
        boolean hasGm() { return gm != null; }
        boolean familyConflict() {
            return hasLm() && hasGm()
                    && lmFamily != MaterialFamily.UNKNOWN
                    && gmFamily != MaterialFamily.UNKNOWN
                    && lmFamily != gmFamily;
        }
        MaterialFamily sampleFamily() {
            if (familyConflict()) return MaterialFamily.UNKNOWN;
            if (lmFamily != MaterialFamily.UNKNOWN) return lmFamily;
            if (gmFamily != MaterialFamily.UNKNOWN) return gmFamily;
            return MaterialFamily.UNKNOWN;
        }
    }
}
