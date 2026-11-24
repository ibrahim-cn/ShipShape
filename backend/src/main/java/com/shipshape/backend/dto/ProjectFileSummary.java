package com.shipshape.backend.dto;

public record ProjectFileSummary(
        String path,
        ProjectFileType type,
        long sizeBytes,
        boolean textContent,
        boolean analyzed,
        FileAnalysisStatus analysisStatus
) {
}
