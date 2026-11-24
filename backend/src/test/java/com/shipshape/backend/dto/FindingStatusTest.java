package com.shipshape.backend.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FindingStatusTest {

    @Test
    void emptyFindingsAreNotChecked() {
        assertEquals(FindingStatus.NOT_CHECKED, FindingStatus.aggregate(List.of()));
        assertEquals(FindingStatus.NOT_CHECKED, FindingStatus.aggregate(null));
    }

    @Test
    void aggregateUsesExplicitPrecedence() {
        assertEquals(FindingStatus.ERROR, aggregate("PASS", "WARNING", "ERROR"));
        assertEquals(FindingStatus.FAIL, aggregate("PASS", "WARNING", "FAIL"));
        assertEquals(FindingStatus.WARNING, aggregate("PASS", "WARNING"));
        assertEquals(FindingStatus.NOT_CHECKED, aggregate("PASS", "NOT_CHECKED"));
        assertEquals(FindingStatus.PASS, aggregate("PASS", "NOT_APPLICABLE"));
        assertEquals(FindingStatus.NOT_APPLICABLE, aggregate("NOT_APPLICABLE"));
    }

    @Test
    void severityIsIndependentFromStatus() {
        Finding finding = new Finding(
                "TEST.RULE", "TEST", "Rule", FindingStatus.FAIL, FindingSeverity.LOW,
                "Explanation", "Action", "Action", null, null, null, List.of()
        );

        assertEquals(FindingStatus.FAIL, finding.getStatus());
        assertEquals(FindingSeverity.LOW, finding.getSeverity());
    }

    @Test
    void legacyFindingUsesUnspecifiedSeverity() {
        Finding finding = new Finding("Rule", FindingStatus.WARNING, "Explanation", "Action");

        assertEquals(FindingSeverity.UNSPECIFIED, finding.getSeverity());
    }

    private String aggregate(String... statuses) {
        return FindingStatus.aggregate(Arrays.stream(statuses)
                .map(status -> new Finding(
                        "TEST.RULE", "TEST", "Rule", status, FindingSeverity.INFO,
                        "Explanation", "Action", "Action", null, null, null, List.of()
                ))
                .map(Finding.class::cast)
                .toList());
    }
}
