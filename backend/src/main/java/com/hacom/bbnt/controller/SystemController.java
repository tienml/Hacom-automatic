package com.hacom.bbnt.controller;

import com.hacom.bbnt.dto.SystemStatusResponse;
import com.hacom.bbnt.service.PdfConversionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    private final PdfConversionService pdfConversionService;

    public SystemController(PdfConversionService pdfConversionService) {
        this.pdfConversionService = pdfConversionService;
    }

    @GetMapping("/status")
    public SystemStatusResponse status() {
        var status = pdfConversionService.status();
        return new SystemStatusResponse(
                "HaCom BBNT Automation",
                "1.0.0",
                status.configuredMode(),
                status.activeEngine(),
                status.available(),
                status.message()
        );
    }
}
