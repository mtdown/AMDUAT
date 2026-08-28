package com.amduat.scanner.model;

import java.time.Instant;

public record ScanSummary(
        String rootPath,
        boolean found,
        int presetCount,
        int cordisEntryCount,
        int packageCount,
        int parseFailureCount,
        Instant scannedAt
) {
}
