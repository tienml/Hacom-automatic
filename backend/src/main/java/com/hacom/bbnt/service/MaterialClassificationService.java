package com.hacom.bbnt.service;

import com.hacom.bbnt.model.MaterialFamily;
import org.springframework.stereotype.Service;

@Service
public class MaterialClassificationService {
    public Classification classify(String workContent) {
        String normalized = TextNormalizer.asciiLower(workContent);
        boolean mortar = containsWord(normalized, "vua") || normalized.contains("vxm");
        boolean concrete = normalized.contains("be tong") || normalized.contains("beton");
        if (mortar && !concrete) {
            return new Classification(MaterialFamily.VUA, "Nội dung DM chứa từ khóa vữa/VXM.");
        }
        if (concrete && !mortar) {
            return new Classification(MaterialFamily.BETONG, "Nội dung DM chứa từ khóa bê tông/beton.");
        }
        if (mortar) {
            return new Classification(MaterialFamily.UNKNOWN, "Nội dung đồng thời chứa dấu hiệu vữa và bê tông.");
        }
        return new Classification(MaterialFamily.UNKNOWN, "Không tìm thấy từ khóa vật liệu chắc chắn trong nội dung DM.");
    }

    private boolean containsWord(String value, String word) {
        return (" " + value + " ").matches(".*[^a-z0-9]" + word + "[^a-z0-9].*");
    }

    public record Classification(MaterialFamily materialFamily, String reason) {
    }
}
