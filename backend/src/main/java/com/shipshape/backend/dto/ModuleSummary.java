package com.shipshape.backend.dto;

public record ModuleSummary(
        String module,
        String status,
        int findingCount
) {
}
