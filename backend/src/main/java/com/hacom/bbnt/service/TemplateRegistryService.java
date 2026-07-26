package com.hacom.bbnt.service;

import com.hacom.bbnt.error.ApiException;
import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.MaterialFamily;
import com.hacom.bbnt.model.ParsedSheetName;
import com.hacom.bbnt.model.TemplatePair;
import com.hacom.bbnt.model.TemplateProfile;
import com.hacom.bbnt.model.TemplateRegistry;
import org.apache.poi.hssf.usermodel.HSSFPatriarch;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TemplateRegistryService {
    private final SheetNameParser sheetNameParser;
    private final TemplateProfileService profileService;

    public TemplateRegistryService(SheetNameParser sheetNameParser, TemplateProfileService profileService) {
        this.sheetNameParser = sheetNameParser;
        this.profileService = profileService;
    }

    public TemplateRegistry build(Workbook workbook) {
        Map<MaterialFamily, Map<String, PairCandidate>> grouped = new EnumMap<>(MaterialFamily.class);
        Map<String, TemplateProfile> profiles = new LinkedHashMap<>();
        List<MainCandidate> mainCandidates = new ArrayList<>();

        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            Sheet sheet = workbook.getSheetAt(index);
            ParsedSheetName parsed = sheetNameParser.parse(sheet.getSheetName()).orElse(null);
            if (parsed == null) continue;
            if (parsed.mainSheet()) {
                mainCandidates.add(inspectMainSheet(workbook, sheet, index));
                continue;
            }
            if (parsed.materialFamily() == MaterialFamily.UNKNOWN) continue;

            PairCandidate candidate = grouped
                    .computeIfAbsent(parsed.materialFamily(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(parsed.itemNumber(), ignored -> new PairCandidate(parsed.itemNumber()));
            candidate.firstWorkbookIndex = Math.min(candidate.firstWorkbookIndex, index);
            CandidateSheet metadata = inspectSheet(workbook, sheet, index, parsed);
            if (parsed.documentType() == DocumentType.LM && candidate.lm == null) candidate.lm = metadata;
            if (parsed.documentType() == DocumentType.GM && candidate.gm == null) candidate.gm = metadata;
            if (metadata.profile != null) profiles.put(sheet.getSheetName(), metadata.profile);
        }

        Map<MaterialFamily, List<TemplatePair>> candidatesByFamily = new EnumMap<>(MaterialFamily.class);
        Map<MaterialFamily, TemplatePair> recommended = new EnumMap<>(MaterialFamily.class);
        for (MaterialFamily family : List.of(MaterialFamily.VUA, MaterialFamily.BETONG)) {
            List<PairCandidate> groupedCandidates = new ArrayList<>(grouped.getOrDefault(family, Map.of()).values());
            groupedCandidates.removeIf(candidate -> candidate.lm == null || candidate.gm == null);
            List<TemplatePair> pairs = groupedCandidates.stream()
                    .map(candidate -> toPair(family, candidate))
                    .sorted(Comparator.comparingInt(this::score).reversed()
                            .thenComparing(TemplatePair::lmSheetName))
                    .toList();
            candidatesByFamily.put(family, pairs);
            pairs.stream().filter(TemplatePair::usable).max(Comparator.comparingInt(this::score))
                    .ifPresent(pair -> recommended.put(family, withRecommendedReason(pair)));
        }

        List<String> mainTemplates = mainCandidates.stream()
                .filter(candidate -> candidate.profileResolvable)
                .sorted(Comparator.comparingInt((MainCandidate candidate) -> candidate.score).reversed()
                        .thenComparing(candidate -> candidate.sheetName))
                .map(candidate -> candidate.sheetName)
                .toList();
        return new TemplateRegistry(candidatesByFamily, recommended, profiles, mainTemplates);
    }

    /** Kiểm tra 1 sheet MAIN có đủ điều kiện làm layout nguồn để clone không (profile đọc được + có in ấn/merge hợp lý). */
    private MainCandidate inspectMainSheet(Workbook workbook, Sheet sheet, int index) {
        boolean profileResolvable;
        try {
            profileService.resolve(sheet, MaterialFamily.UNKNOWN, DocumentType.MAIN);
            profileResolvable = true;
        } catch (RuntimeException exception) {
            // Bắt rộng RuntimeException (không chỉ ApiException) để 1 sheet MAIN dị dạng/lỗi bất ngờ
            // không bao giờ làm hỏng cả vòng quét template của các sheet khác (LM/GM/MAIN còn lại).
            profileResolvable = false;
        }
        String printArea = workbook.getPrintArea(index);
        int score = (profileResolvable ? 10_000 : 0)
                + (printArea != null && !printArea.isBlank() ? 1_000 : 0)
                + sheet.getNumMergedRegions()
                + drawingCount(sheet) * 100;
        return new MainCandidate(sheet.getSheetName(), profileResolvable, score);
    }

    private record MainCandidate(String sheetName, boolean profileResolvable, int score) {
    }

    private CandidateSheet inspectSheet(Workbook workbook, Sheet sheet, int index, ParsedSheetName parsed) {
        List<String> warnings = new ArrayList<>();
        TemplateProfile profile = null;
        try {
            profile = profileService.resolve(sheet, parsed.materialFamily(), parsed.documentType());
            warnings.addAll(profile.warnings());
        } catch (ApiException exception) {
            warnings.add(exception.getMessage());
        }
        String printArea = workbook.getPrintArea(index);
        if (printArea == null || printArea.isBlank()) warnings.add("Template không khai báo print area.");
        if (sheet.getNumMergedRegions() < 5) warnings.add("Template có ít merged regions; cần kiểm tra layout.");
        return new CandidateSheet(
                sheet.getSheetName(),
                profile,
                sheet.getNumMergedRegions(),
                drawingCount(sheet),
                printArea != null && !printArea.isBlank(),
                List.copyOf(warnings)
        );
    }

    private TemplatePair toPair(MaterialFamily family, PairCandidate candidate) {
        boolean compatible = candidate.lm.profile != null && candidate.gm.profile != null;
        List<String> warnings = new ArrayList<>();
        warnings.addAll(candidate.lm.warnings);
        warnings.addAll(candidate.gm.warnings);
        int merged = candidate.lm.mergedRegionCount + candidate.gm.mergedRegionCount;
        int drawings = candidate.lm.drawingCount + candidate.gm.drawingCount;
        boolean printArea = candidate.lm.hasPrintArea && candidate.gm.hasPrintArea;
        String reason = compatible
                ? "Cặp LM/GM cùng item, đã xác minh profile, merged regions, drawing và thiết lập in."
                : "Cặp sheet cùng item nhưng có template không khớp profile; không được tự động sử dụng.";
        return new TemplatePair(
                family,
                candidate.lm.sheetName,
                candidate.gm.sheetName,
                reason,
                compatible,
                merged,
                drawings,
                printArea,
                warnings
        );
    }

    private TemplatePair withRecommendedReason(TemplatePair pair) {
        return new TemplatePair(
                pair.materialFamily(),
                pair.lmSheetName(),
                pair.gmSheetName(),
                "Template đề xuất có điểm cấu trúc tốt nhất: profile tương thích, "
                        + pair.mergedRegionCount() + " merged regions, "
                        + pair.drawingCount() + " drawing(s), print area=" + pair.hasPrintArea() + ".",
                pair.profileCompatible(),
                pair.mergedRegionCount(),
                pair.drawingCount(),
                pair.hasPrintArea(),
                pair.validationWarnings()
        );
    }

    private int score(TemplatePair pair) {
        return (pair.profileCompatible() ? 10_000 : 0)
                + (pair.hasPrintArea() ? 1_000 : 0)
                + pair.drawingCount() * 100
                + pair.mergedRegionCount();
    }

    private int drawingCount(Sheet sheet) {
        if (sheet instanceof XSSFSheet xssfSheet) {
            XSSFDrawing drawing = xssfSheet.getDrawingPatriarch();
            return drawing == null ? 0 : drawing.getShapes().size();
        }
        if (sheet instanceof HSSFSheet hssfSheet) {
            HSSFPatriarch drawing = hssfSheet.getDrawingPatriarch();
            return drawing == null ? 0 : drawing.getChildren().size();
        }
        return 0;
    }

    private static final class PairCandidate {
        private final String itemNumber;
        private CandidateSheet lm;
        private CandidateSheet gm;
        private int firstWorkbookIndex = Integer.MAX_VALUE;

        private PairCandidate(String itemNumber) {
            this.itemNumber = itemNumber;
        }
    }

    private record CandidateSheet(
            String sheetName,
            TemplateProfile profile,
            int mergedRegionCount,
            int drawingCount,
            boolean hasPrintArea,
            List<String> warnings
    ) {
    }
}
