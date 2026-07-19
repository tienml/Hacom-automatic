package com.hacom.bbnt;

import com.hacom.bbnt.service.DescriptionService;
import com.hacom.bbnt.service.DocumentGenerationService;
import com.hacom.bbnt.service.DocumentNumberService;
import com.hacom.bbnt.service.ExcelAnalysisService;
import com.hacom.bbnt.service.FieldDecisionService;
import com.hacom.bbnt.service.GeneratedSheetValidator;
import com.hacom.bbnt.service.MaterialClassificationService;
import com.hacom.bbnt.service.OutputSheetService;
import com.hacom.bbnt.service.PdfConversionService;
import com.hacom.bbnt.service.SheetNameParser;
import com.hacom.bbnt.service.TemplateCloneService;
import com.hacom.bbnt.service.TemplateProfileService;
import com.hacom.bbnt.service.TemplateRegistryService;
import com.hacom.bbnt.service.TemporaryStore;
import com.hacom.bbnt.service.WorkItemPlanningService;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;

record TestServices(
        TemporaryStore store,
        ExcelAnalysisService analysis,
        DocumentGenerationService generation,
        OutputSheetService outputs
) {
    static TestServices create(Path temporaryDirectory, boolean enablePdf) throws Exception {
        TemporaryStore store = new TemporaryStore(temporaryDirectory.toString(), 60);
        SheetNameParser parser = new SheetNameParser();
        DocumentNumberService numberService = new DocumentNumberService();
        DescriptionService descriptionService = new DescriptionService();
        FieldDecisionService fieldDecisionService = new FieldDecisionService(numberService, descriptionService);
        MaterialClassificationService classifier = new MaterialClassificationService();
        TemplateProfileService profileService = new TemplateProfileService();
        TemplateRegistryService registry = new TemplateRegistryService(parser, profileService);
        WorkItemPlanningService planner = new WorkItemPlanningService(classifier, parser, fieldDecisionService);
        ExcelAnalysisService analysis = new ExcelAnalysisService(store, parser, registry, planner);
        GeneratedSheetValidator validator = new GeneratedSheetValidator();
        TemplateCloneService cloneService = new TemplateCloneService(profileService, fieldDecisionService, validator);
        PdfConversionService pdfService = new PdfConversionService(
                RestClient.builder(), enablePdf ? "libreoffice" : "disabled",
                "http://localhost:3000", "soffice", 180
        );
        DocumentGenerationService generation = new DocumentGenerationService(store, pdfService, cloneService, parser);
        OutputSheetService outputs = new OutputSheetService(store, parser, fieldDecisionService);
        return new TestServices(store, analysis, generation, outputs);
    }
}
