package com.amduat.scanner.model;

import java.util.Locale;
import java.util.Objects;

public record Accessory(
        String id,
        String kind,
        String category,
        String displayName,
        String backendName,
        String entryId,
        String presetId,
        String presetName,
        Boolean enabled,
        String version,
        String description,
        String source,
        String sourcePath,
        String parseStatus,
        String parseMessage
) {
    public static Accessory cordisEntry(
            String entryId,
            String backendName,
            String presetId,
            String presetName,
            Boolean enabled,
            String source,
            String sourcePath,
            String parseStatus,
            String parseMessage
    ) {
        String displayName = backendName == null || backendName.isBlank() ? entryId : backendName;
        return new Accessory(
                stableId("cordis", sourcePath, entryId, backendName),
                "cordis_entry",
                classify(backendName, sourcePath),
                displayName,
                emptyToNull(backendName),
                entryId,
                presetId,
                presetName,
                enabled,
                null,
                "",
                source,
                sourcePath,
                parseStatus,
                parseMessage
        );
    }

    public static Accessory packageAccessory(String backendName, String version, String description, String sourcePath) {
        return new Accessory(
                stableId("package", sourcePath, backendName, version),
                "package",
                classify(backendName, sourcePath),
                backendName,
                backendName,
                null,
                null,
                null,
                true,
                emptyToNull(version),
                emptyToNull(description),
                "package_json",
                sourcePath,
                "ok",
                ""
        );
    }

    public static Accessory failedCordisFile(String sourcePath, String source, String parseMessage) {
        return new Accessory(
                stableId("cordis-failed", sourcePath, parseMessage),
                "cordis_entry",
                "other",
                "解析失败的 Cordis 文件",
                null,
                null,
                null,
                null,
                null,
                null,
                "",
                source,
                sourcePath,
                "failed",
                parseMessage
        );
    }

    public static Accessory failedPackage(String sourcePath, String parseMessage) {
        return new Accessory(
                stableId("package-failed", sourcePath, parseMessage),
                "package",
                "other",
                "解析失败的 package.json",
                null,
                null,
                null,
                null,
                null,
                null,
                "",
                "package_json",
                sourcePath,
                "failed",
                parseMessage
        );
    }

    public static String classify(String backendName, String sourcePath) {
        String name = backendName == null ? "" : backendName.toLowerCase(Locale.ROOT);
        String path = sourcePath == null ? "" : sourcePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (name.equals("@deepseek-ai/dsh-tool-skill") || name.startsWith("@deepseek-ai/dsh-skill-")) {
            return "skill";
        }
        if (name.equals("@deepseek-ai/dsh-tool-workflow") || name.startsWith("@deepseek-ai/dsh-workflow-")) {
            return "workflow";
        }
        if (name.startsWith("@deepseek-ai/dsh-tool-")) {
            return "tool";
        }
        if (name.equals("@deepseek-ai/dsh-agent-presets")) {
            return "agent_preset";
        }
        if (name.startsWith("@deepseek-ai/dsh-host-")) {
            return "host";
        }
        if (name.startsWith("@deepseek-ai/dsh-client-")) {
            return "client";
        }
        if (name.equals("@deepseek-ai/dsh-persona") || name.equals("@deepseek-ai/dsh-agent-instructions")) {
            return "prompt";
        }
        if (path.contains("/apps/")) {
            return "app";
        }
        if (path.contains("/packages/bundle/")) {
            return "bundle";
        }
        return "other";
    }

    private static String stableId(String prefix, Object... parts) {
        return prefix + "-" + Integer.toUnsignedString(Objects.hash(parts), 36);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
