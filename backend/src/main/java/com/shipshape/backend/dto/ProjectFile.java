package com.shipshape.backend.dto;

public record ProjectFile(
        String path,
        ProjectFileType type,
        long sizeBytes,
        boolean textContent,
        String content
) {
}
