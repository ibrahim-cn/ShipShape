package com.shipshape.backend.dto;

import java.util.List;

public record ProjectAnalysisResponse(
        String source,
        String projectName,
        String overallStatus,
        boolean coverageComplete,
        List<String> implementedModules,
        List<String> incompleteModules,
        List<String> notCheckedModules,
        List<Finding> findings,
        List<ModuleSummary> modules,
        List<ProjectFileSummary> inventory,
        List<String> limitations
) {
}
