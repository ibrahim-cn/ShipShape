package com.shipshape.backend.dto;

import java.util.List;

public record ProjectContext(
        String source,
        String projectName,
        List<ProjectFile> files
) {
    public ProjectContext {
        files = files == null ? List.of() : List.copyOf(files);
        projectName = projectName == null || projectName.isBlank() ? "uploaded-project" : projectName.trim();
    }
}
