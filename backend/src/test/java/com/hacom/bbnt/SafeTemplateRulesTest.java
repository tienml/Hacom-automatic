package com.hacom.bbnt;

import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.MaterialFamily;
import com.hacom.bbnt.service.DescriptionService;
import com.hacom.bbnt.service.DocumentNumberService;
import com.hacom.bbnt.service.MaterialClassificationService;
import com.hacom.bbnt.service.SheetNameParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafeTemplateRulesTest {
    private final MaterialClassificationService classifier = new MaterialClassificationService();
    private final DocumentNumberService numberService = new DocumentNumberService();
    private final DescriptionService descriptionService = new DescriptionService();
    private final SheetNameParser parser = new SheetNameParser();

    @Test
    void detectsVietnameseAccentedAndUnaccentedMaterialNames() {
        assertThat(classifier.classify("Chất lượng VỮA trát").materialFamily()).isEqualTo(MaterialFamily.VUA);
        assertThat(classifier.classify("Chat luong vua trat").materialFamily()).isEqualTo(MaterialFamily.VUA);
        assertThat(classifier.classify("BÊ TÔNG lót").materialFamily()).isEqualTo(MaterialFamily.BETONG);
        assertThat(classifier.classify("beton lot").materialFamily()).isEqualTo(MaterialFamily.BETONG);
        assertThat(classifier.classify("Lắp đặt cửa").materialFamily()).isEqualTo(MaterialFamily.UNKNOWN);
    }

    @Test
    void convertsDocumentNumberBySegmentInsteadOfCharacterIndex() {
        assertThat(numberService.convert("1503/CB/NTCV/159", DocumentType.LM)).isEqualTo("1503/CB/LM/159");
        assertThat(numberService.convert("LONG-PREFIX/CB/NTCV/159", DocumentType.GM)).isEqualTo("LONG-PREFIX/CB/GM/159");
        assertThat(numberService.convert("khong-co-cau-truc", DocumentType.LM)).isBlank();
    }

    @Test
    void removesOnlyLeadingQualityPrefix() {
        assertThat(descriptionService.description(
                DocumentType.LM,
                "  CHẤT LƯỢNG   vữa trát có yêu cầu chất lượng cao ",
                "Tầng 2"
        )).isEqualTo("Lấy mẫu vữa trát có yêu cầu chất lượng cao (Tầng 2)");
        assertThat(descriptionService.description(DocumentType.GM, "Kiểm tra chất lượng vữa", ""))
                .isEqualTo("Mẫu Kiểm tra chất lượng vữa");
    }

    @Test
    void parsesSheetNamesWithoutConfusingLeadingSequence() {
        var parsed = parser.parse(" 1.LMV (159) ").orElseThrow();
        assertThat(parsed.documentType()).isEqualTo(DocumentType.LM);
        assertThat(parsed.materialFamily()).isEqualTo(MaterialFamily.VUA);
        assertThat(parsed.itemNumber()).isEqualTo("159");
        assertThat(parser.parse("1.GMBT (106A)").orElseThrow().itemNumber()).isEqualTo("106A");
        assertThat(parser.plannedSheetName(DocumentType.GM, MaterialFamily.BETONG, "106a"))
                .isEqualTo("1.GMBT (106A)");
    }
}
