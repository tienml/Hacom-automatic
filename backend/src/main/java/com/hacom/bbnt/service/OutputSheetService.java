package com.hacom.bbnt.service;

import com.hacom.bbnt.dto.DocumentPlanDto;
import com.hacom.bbnt.dto.FieldDecisionDto;
import com.hacom.bbnt.dto.OutputSheetDto;
import com.hacom.bbnt.dto.WorkItemDto;
import com.hacom.bbnt.error.ApiException;
import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.GenerationMode;
import com.hacom.bbnt.model.JobContext;
import com.hacom.bbnt.model.MaterialFamily;
import com.hacom.bbnt.model.OutputAvailability;
import com.hacom.bbnt.model.TemplatePair;
import com.hacom.bbnt.model.TemplateProfile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class OutputSheetService {
    private final TemporaryStore store;
    private final SheetNameParser sheetNameParser;
    private final FieldDecisionService fieldDecisionService;

    public OutputSheetService(
            TemporaryStore store,
            SheetNameParser sheetNameParser,
            FieldDecisionService fieldDecisionService
    ) {
        this.store = store;
        this.sheetNameParser = sheetNameParser;
        this.fieldDecisionService = fieldDecisionService;
    }

    public List<OutputSheetDto> outputs(
            String jobId,
            String rawItemNumber,
            MaterialFamily requestedFamily,
            String requestedLmTemplate,
            String requestedGmTemplate
    ) {
        JobContext job = store.getJob(jobId);
        String itemNumber = sheetNameParser.normalizeItemNumber(rawItemNumber);
        WorkItemDto item = job.workItem(itemNumber);
        if (item == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "Không tìm thấy số danh mục " + itemNumber + " trong sheet DM.");
        }

        MaterialFamily family = resolveRequestedFamily(item, requestedFamily);
        List<OutputSheetDto> outputs = new ArrayList<>();
        if (item.mainPlan() != null && item.mainPlan().availability() == OutputAvailability.EXISTING) {
            outputs.add(existingOutput(item.mainPlan(), itemNumber, item.materialFamily()));
        } else if (item.mainPlan() != null && item.mainPlan().availability() == OutputAvailability.GENERATABLE) {
            outputs.add(mainOutput(item.mainPlan(), itemNumber));
        }
        outputs.add(sampleOutput(job, item, DocumentType.LM, family, requestedLmTemplate));
        outputs.add(sampleOutput(job, item, DocumentType.GM, family, requestedGmTemplate));
        return outputs.stream().filter(java.util.Objects::nonNull).toList();
    }

    private OutputSheetDto mainOutput(DocumentPlanDto plan, String itemNumber) {
        return new OutputSheetDto(
                plan.plannedSheetName(),
                "Biểu mẫu chính — sẽ tạo từ " + plan.sourceTemplate(),
                DocumentType.MAIN.name(),
                DocumentType.MAIN,
                "Tạo mới từ layout " + plan.sourceTemplate() + "; chỉ điền dữ liệu CERTAIN — hồ sơ chính cho DM " + itemNumber,
                true,
                true,
                plan.sourceTemplate(),
                plan.availableSourceTemplates(),
                GenerationMode.CLONE_TEMPLATE,
                OutputAvailability.GENERATABLE,
                MaterialFamily.UNKNOWN,
                plan.fieldDecisions(),
                plan.warnings()
        );
    }

    public List<OutputSheetDto> outputs(String jobId, String rawItemNumber, MaterialFamily requestedFamily) {
        return outputs(jobId, rawItemNumber, requestedFamily, null, null);
    }

    private MaterialFamily resolveRequestedFamily(WorkItemDto item, MaterialFamily requestedFamily) {
        MaterialFamily existingFamily = existingSampleFamily(item);
        MaterialFamily explicit = requestedFamily == null ? MaterialFamily.UNKNOWN : requestedFamily;
        if (existingFamily != MaterialFamily.UNKNOWN) {
            if (explicit != MaterialFamily.UNKNOWN && explicit != existingFamily) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "DM " + item.itemNumber() + " đã có LM/GM thuộc " + existingFamily
                                + "; không thể lập kế hoạch sheet còn thiếu theo " + explicit + ".");
            }
            return existingFamily;
        }
        return explicit == MaterialFamily.UNKNOWN ? item.materialFamily() : explicit;
    }

    private MaterialFamily existingSampleFamily(WorkItemDto item) {
        MaterialFamily lm = familyOfExisting(item.lmPlan());
        MaterialFamily gm = familyOfExisting(item.gmPlan());
        if (lm != MaterialFamily.UNKNOWN && gm != MaterialFamily.UNKNOWN && lm != gm) {
            return MaterialFamily.UNKNOWN;
        }
        return lm != MaterialFamily.UNKNOWN ? lm : gm;
    }

    private MaterialFamily familyOfExisting(DocumentPlanDto plan) {
        if (plan == null || plan.availability() != OutputAvailability.EXISTING
                || plan.existingSheetName() == null) return MaterialFamily.UNKNOWN;
        var parsed = sheetNameParser.parse(plan.existingSheetName()).orElse(null);
        return parsed == null ? MaterialFamily.UNKNOWN : parsed.materialFamily();
    }

    private OutputSheetDto sampleOutput(
            JobContext job,
            WorkItemDto item,
            DocumentType type,
            MaterialFamily family,
            String requestedTemplate
    ) {
        DocumentPlanDto existingPlan = type == DocumentType.LM ? item.lmPlan() : item.gmPlan();
        if (existingPlan != null && existingPlan.availability() == OutputAvailability.EXISTING) {
            return existingOutput(existingPlan, item.itemNumber(), item.materialFamily());
        }
        if (family == MaterialFamily.UNKNOWN) return null;

        List<String> availableTemplates = availableTemplates(job, family, type);
        String template = chooseTemplate(job, family, type, requestedTemplate);
        String plannedName = sheetNameParser.plannedSheetName(type, family, item.itemNumber());
        if (template == null) {
            String warning = "Không có template " + type + " tương thích profile cho " + materialLabel(family) + ".";
            return new OutputSheetDto(
                    plannedName,
                    displayGenerated(type, null),
                    type.name(),
                    type,
                    warning,
                    false,
                    true,
                    null,
                    availableTemplates,
                    null,
                    OutputAvailability.MISSING_TEMPLATE,
                    family,
                    List.of(),
                    List.of(warning)
            );
        }

        TemplateProfile profile = job.templateRegistry().profileFor(template);
        if (profile == null || profile.documentType() != type || profile.materialFamily() != family) {
            String warning = "Template " + template + " không khớp profile " + type + "/" + family + ".";
            return new OutputSheetDto(
                    plannedName,
                    displayGenerated(type, template),
                    type.name(),
                    type,
                    warning,
                    false,
                    true,
                    template,
                    availableTemplates,
                    null,
                    OutputAvailability.MISSING_TEMPLATE,
                    family,
                    List.of(),
                    List.of(warning)
            );
        }
        List<FieldDecisionDto> decisions = fieldDecisionService.decisions(item, job.project(), profile, type);
        List<String> warnings = new ArrayList<>(profile.warnings());
        TemplatePair pair = pairContaining(job, family, template, type);
        if (pair != null) warnings.addAll(pair.validationWarnings());
        return new OutputSheetDto(
                plannedName,
                displayGenerated(type, template),
                type.name(),
                type,
                description(type, family, item.itemNumber(), true),
                true,
                true,
                template,
                availableTemplates,
                GenerationMode.CLONE_TEMPLATE,
                OutputAvailability.GENERATABLE,
                family,
                decisions,
                List.copyOf(new java.util.LinkedHashSet<>(warnings))
        );
    }

    private OutputSheetDto existingOutput(DocumentPlanDto plan, String itemNumber, MaterialFamily fallbackFamily) {
        DocumentType type = plan.documentType();
        MaterialFamily family = plan.materialFamily() == MaterialFamily.UNKNOWN ? fallbackFamily : plan.materialFamily();
        String name = plan.existingSheetName();
        return new OutputSheetDto(
                name,
                displayName(name, itemNumber, type, family),
                type.name(),
                type,
                description(type, family, itemNumber, false),
                true,
                false,
                null,
                plan.availableSourceTemplates(),
                GenerationMode.EXISTING_SHEET,
                OutputAvailability.EXISTING,
                family,
                List.of(),
                plan.warnings()
        );
    }

    private String chooseTemplate(
            JobContext job,
            MaterialFamily family,
            DocumentType type,
            String requestedTemplate
    ) {
        if (requestedTemplate != null && !requestedTemplate.isBlank()) {
            TemplateProfile profile = job.templateRegistry().profileFor(requestedTemplate);
            boolean usableCandidate = job.templateRegistry().pairsFor(family).stream()
                    .filter(TemplatePair::usable)
                    .anyMatch(pair -> type == DocumentType.LM
                            ? pair.lmSheetName().equalsIgnoreCase(requestedTemplate)
                            : pair.gmSheetName().equalsIgnoreCase(requestedTemplate));
            if (profile == null || profile.materialFamily() != family || profile.documentType() != type
                    || !usableCandidate) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Template " + requestedTemplate + " không tương thích hoặc thiếu thiết lập in cho "
                                + type + "/" + family + ".");
            }
            return profile.sheetName();
        }
        TemplatePair recommended = job.templateRegistry().pairFor(family);
        if (recommended == null || !recommended.usable()) return null;
        return type == DocumentType.LM ? recommended.lmSheetName() : recommended.gmSheetName();
    }

    private List<String> availableTemplates(JobContext job, MaterialFamily family, DocumentType type) {
        return job.templateRegistry().pairsFor(family).stream()
                .filter(TemplatePair::usable)
                .map(pair -> type == DocumentType.LM ? pair.lmSheetName() : pair.gmSheetName())
                .distinct()
                .toList();
    }

    private TemplatePair pairContaining(JobContext job, MaterialFamily family, String template, DocumentType type) {
        return job.templateRegistry().pairsFor(family).stream()
                .filter(pair -> type == DocumentType.LM
                        ? pair.lmSheetName().equalsIgnoreCase(template)
                        : pair.gmSheetName().equalsIgnoreCase(template))
                .findFirst().orElse(null);
    }

    private String displayGenerated(DocumentType type, String template) {
        String label = type == DocumentType.LM ? "Lấy mẫu" : "Giao mẫu";
        return template == null ? label + " — thiếu template" : label + " — sẽ tạo từ " + template;
    }

    private String displayName(String name, String itemNumber, DocumentType type, MaterialFamily family) {
        if (type == DocumentType.MAIN || name.trim().equalsIgnoreCase(itemNumber)) return "Biểu mẫu chính — Sheet " + name;
        return switch (type) {
            case LM -> (family == MaterialFamily.BETONG ? "LMBT" : "LMV") + " — " + name;
            case GM -> (family == MaterialFamily.BETONG ? "GMBT" : "GMV") + " — " + name;
            default -> "Biểu mẫu — " + name;
        };
    }

    private String description(DocumentType type, MaterialFamily family, String itemNumber, boolean generated) {
        String source = generated ? "Tạo mới từ template; chỉ điền dữ liệu CERTAIN" : "Sử dụng sheet hiện có";
        return switch (type) {
            case LM -> source + " — phiếu lấy mẫu " + materialLabel(family) + " cho DM " + itemNumber;
            case GM -> source + " — phiếu giao mẫu " + materialLabel(family) + " cho DM " + itemNumber;
            default -> source + " — hồ sơ chính DM " + itemNumber;
        };
    }

    private String materialLabel(MaterialFamily family) {
        return family == MaterialFamily.BETONG ? "bê tông" : family == MaterialFamily.VUA ? "vữa" : "chưa xác định";
    }
}
