package com.amduat.scanner.model;

public record AgentPreset(
        String id,
        String name,
        String description,
        Integer order,
        String sourcePath
) {
}
