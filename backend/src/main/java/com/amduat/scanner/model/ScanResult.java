package com.amduat.scanner.model;

import java.util.List;

public record ScanResult(
        ScanSummary summary,
        List<Accessory> accessories,
        List<AgentPreset> presets
) {
}
