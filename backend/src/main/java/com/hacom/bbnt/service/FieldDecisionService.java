package com.hacom.bbnt.service;

import com.hacom.bbnt.dto.FieldDecisionDto;
import com.hacom.bbnt.dto.ProjectSummary;
import com.hacom.bbnt.dto.WorkItemDto;
import com.hacom.bbnt.model.DataCertainty;
import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.FieldAction;
import com.hacom.bbnt.model.TemplateProfile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FieldDecisionService {
    public static final List<String> UNCERTAIN_FIELDS = List.of(
            "grade",
            "strengthClass",
            "specimenSize",
            "sampleGroupCount",
            "sampleCount",
            "samplesPerGroup",
            "testAge",
            "note",
            "testCriteria",
            "standard",
            "testPurpose",
            "storageLocation",
            "deliveryLocation",
            "deliveryDate",
            "deliveryTime",
            "laboratoryName",
            "laboratoryCode",
            "receiver",
            "laboratoryManager",
            "additionalSampleRows"
    );

    private final DocumentNumberService documentNumberService;
    private final DescriptionService descriptionService;

    public FieldDecisionService(
            DocumentNumberService documentNumberService,
            DescriptionService descriptionService
    ) {
        this.documentNumberService = documentNumberService;
        this.descriptionService = descriptionService;
    }

    public List<FieldDecisionDto> decisions(
            WorkItemDto item,
            ProjectSummary project,
            TemplateProfile profile,
            DocumentType type
    ) {
        List<FieldDecisionDto> decisions = new ArrayList<>();
        addPopulate(decisions, "itemNumber", "DM.columnA", item.itemNumber(), profile.targets("itemNumber"), type,
                "Mã danh mục được đọc trực tiếp từ DM.");
        addPopulateOrClear(decisions, "location", "DM.columnD", item.position(), profile.targets("location"), type,
                "Vị trí được đọc trực tiếp từ DM; nếu DM trống thì ô phải blank thực sự.");
        addPopulate(decisions, "sequenceNumber", "templateStructure", "1", profile.targets("sequenceNumber"), type,
                "Giữ một dòng dữ liệu đầu tiên trong bảng mẫu.");

        if (type == DocumentType.MAIN) {
            addPopulate(decisions, "workContent", "DM.columnC", item.content(), profile.targets("workContent"), type,
                    "Đối tượng nghiệm thu lấy nguyên văn nội dung công việc từ DM, không thêm tiền tố Lấy mẫu/Mẫu.");
            addPopulateOrClear(decisions, "acceptanceDateTime", "DM.columnE", item.inspectionTime(),
                    profile.targets("acceptanceDateTime"), type,
                    "Ngày giờ nghiệm thu lấy trực tiếp từ DM; nếu DM trống thì để trống thực sự.");
            addPopulateOrClear(decisions, "acceptanceNumber", "DM.columnF", item.recordNumber(),
                    profile.targets("acceptanceNumber"), type,
                    "Số biên bản nghiệm thu (NTCV) lấy trực tiếp từ DM.");
            addPopulateOrClear(decisions, "requestNumber", "acceptanceNumber.segmentConversion",
                    documentNumberService.convert(item.recordNumber(), "YCNT"), profile.targets("requestNumber"), type,
                    "Số phiếu yêu cầu nghiệm thu (YCNT) suy ra bằng cách thay segment loại hồ sơ từ số biên bản.");
        } else if (type == DocumentType.LM) {
            addPopulateOrClear(decisions, "sampleDate", "DM.columnG", item.sampleDate(), profile.targets("sampleDate"), type,
                    "Chỉ điền khi DM có ngày lấy mẫu hợp lệ.");
            addPopulateOrClear(decisions, "lmNumber", "acceptanceNumber.segmentConversion",
                    documentNumberService.convert(item.recordNumber(), DocumentType.LM), profile.targets("lmNumber"), type,
                    "Thay segment loại hồ sơ bằng LM.");
            addPopulate(decisions, "lmDescription", "DM.columnC+DM.columnD",
                    descriptionService.description(DocumentType.LM, item.content(), item.position()),
                    profile.targets("lmDescription"), type,
                    "Mô tả được tạo từ nội dung và vị trí DM.");
        } else {
            addPopulateOrClear(decisions, "gmNumber", "acceptanceNumber.segmentConversion",
                    documentNumberService.convert(item.recordNumber(), DocumentType.GM), profile.targets("gmNumber"), type,
                    "Thay segment loại hồ sơ bằng GM.");
            addPopulateOrClear(decisions, "lmNumber", "acceptanceNumber.segmentConversion",
                    documentNumberService.convert(item.recordNumber(), DocumentType.LM), profile.targets("lmNumber"), type,
                    "Số LM liên quan được chuyển theo segment.");
            addPopulateOrClear(decisions, "sampleDate", "DM.columnG", item.sampleDate(), profile.targets("sampleDate"), type,
                    "Ngày lấy mẫu lấy từ DM; ngày giao mẫu không được suy đoán.");
            addPopulate(decisions, "gmDescription", "DM.columnC+DM.columnD",
                    descriptionService.description(DocumentType.GM, item.content(), item.position()),
                    profile.targets("gmDescription"), type,
                    "Mô tả được tạo từ nội dung và vị trí DM.");
        }

        addProjectDecision(decisions, "projectName", project == null ? null : project.projectName(),
                "PROJECT_LEVEL_DATA.projectName", profile.targets("projectName"), type);
        addProjectDecision(decisions, "packageName", project == null ? null : project.packageName(),
                "PROJECT_LEVEL_DATA.packageName", profile.targets("packageName"), type);
        addProjectDecision(decisions, "projectLocation", project == null ? null : project.location(),
                "PROJECT_LEVEL_DATA.location", profile.targets("projectLocation"), type);
        addProjectDecision(decisions, "contractor", project == null ? null : project.contractor(),
                "PROJECT_LEVEL_DATA.contractor", profile.targets("contractor"), type);

        if (type != DocumentType.MAIN) {
            for (String field : UNCERTAIN_FIELDS) {
                List<String> targetRanges = field.equals("additionalSampleRows")
                        ? profile.uncertainRanges()
                        : List.of();
                decisions.add(new FieldDecisionDto(
                        field,
                        "template/unknown",
                        null,
                        DataCertainty.UNCERTAIN,
                        FieldAction.CLEAR,
                        profile.targets(field),
                        targetRanges,
                        type,
                        "Không có nguồn CERTAIN trong DM hoặc cấu hình dự án; phải xóa khỏi bản clone."
                ));
            }
        }
        return List.copyOf(decisions);
    }

    private void addPopulate(
            List<FieldDecisionDto> decisions,
            String field,
            String source,
            String value,
            List<String> targets,
            DocumentType type,
            String reason
    ) {
        if (value == null || value.isBlank() || targets.isEmpty()) return;
        decisions.add(new FieldDecisionDto(field, source, value, DataCertainty.CERTAIN, FieldAction.POPULATE,
                targets, List.of(), type, reason));
    }

    private void addPopulateOrClear(
            List<FieldDecisionDto> decisions,
            String field,
            String source,
            String value,
            List<String> targets,
            DocumentType type,
            String reason
    ) {
        if (targets.isEmpty()) return;
        if (value == null || value.isBlank()) {
            decisions.add(new FieldDecisionDto(field, source, null, DataCertainty.UNKNOWN, FieldAction.CLEAR,
                    targets, List.of(), type, reason));
        } else {
            addPopulate(decisions, field, source, value, targets, type, reason);
        }
    }

    private void addProjectDecision(
            List<FieldDecisionDto> decisions,
            String field,
            String value,
            String source,
            List<String> targets,
            DocumentType type
    ) {
        if (targets.isEmpty()) return;
        if (value == null || value.isBlank()) {
            decisions.add(new FieldDecisionDto(
                    field,
                    source,
                    null,
                    DataCertainty.UNKNOWN,
                    FieldAction.CLEAR,
                    targets,
                    List.of(),
                    type,
                    "Không đọc được dữ liệu project-level từ workbook; không được giữ giá trị cụ thể của template."
            ));
        } else {
            decisions.add(new FieldDecisionDto(field, source, value, DataCertainty.CERTAIN, FieldAction.POPULATE,
                    targets, List.of(), type, "Dữ liệu project-level được đọc từ chính workbook."));
        }
    }
}
