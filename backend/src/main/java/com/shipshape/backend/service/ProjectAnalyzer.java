package com.shipshape.backend.service;

import com.shipshape.backend.dto.Finding;
import com.shipshape.backend.dto.ProjectAnalyzerFileResult;
import com.shipshape.backend.dto.ProjectContext;

import java.util.List;

public interface ProjectAnalyzer {
    String module();

    List<Finding> analyze(ProjectContext context);

    default List<ProjectAnalyzerFileResult> analyzedFiles(ProjectContext context, List<Finding> findings) {
        return List.of();
    }
}
