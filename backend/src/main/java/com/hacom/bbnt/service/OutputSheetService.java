package com.hacom.bbnt.service;

import com.hacom.bbnt.dto.OutputSheetDto;
import com.hacom.bbnt.error.ApiException;
import com.hacom.bbnt.model.JobContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class OutputSheetService {
    private final TemporaryStore store;

    public OutputSheetService(TemporaryStore store) {
        this.store = store;
    }

    public List<OutputSheetDto> outputs(String jobId, int workItemNumber) {
        JobContext job = store.getJob(jobId);
        boolean exists = job.workItems().stream().anyMatch(item -> item.number() == workItemNumber);
        if (!exists) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "Không tìm thấy số danh mục " + workItemNumber + " trong sheet DM.");
        }
        return job.outputSheets().getOrDefault(workItemNumber, List.of()).stream()
                .map(name -> new OutputSheetDto(
                        name,
                        displayName(name, workItemNumber),
                        detectType(name),
                        description(name, workItemNumber),
                        true
                ))
                .toList();
    }

    private String displayName(String name, int number) {
        if (name.trim().equals(String.valueOf(number))) {
            return "Biểu mẫu chính — Sheet " + name;
        }
        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.contains("LMV")) return "LMV — " + name;
        if (upper.contains("GMV")) return "GMV — " + name;
        if (upper.contains("LMBT")) return "LMBT — " + name;
        if (upper.contains("GMBT")) return "GMBT — " + name;
        return "Biểu mẫu — " + name;
    }

    private String description(String name, int number) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (name.trim().equals(String.valueOf(number))) {
            return "Biểu mẫu nghiệm thu chính theo danh mục " + number;
        }
        if (upper.contains("LMV")) return "Biểu mẫu lấy mẫu vữa theo danh mục " + number;
        if (upper.contains("GMV")) return "Biểu mẫu giao mẫu vữa theo danh mục " + number;
        if (upper.contains("LMBT")) return "Biểu mẫu lấy mẫu bê tông theo danh mục " + number;
        if (upper.contains("GMBT")) return "Biểu mẫu giao mẫu bê tông theo danh mục " + number;
        return "Sheet đầu ra liên quan đến danh mục " + number;
    }

    private String detectType(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.contains("LMV")) return "LMV";
        if (upper.contains("GMV")) return "GMV";
        if (upper.contains("LMBT")) return "LMBT";
        if (upper.contains("GMBT")) return "GMBT";
        return "MAIN";
    }
}
