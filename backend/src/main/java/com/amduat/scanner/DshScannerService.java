package com.amduat.scanner;

import com.amduat.scanner.model.Accessory;
import com.amduat.scanner.model.AgentPreset;
import com.amduat.scanner.model.ScanResult;
import com.amduat.scanner.model.ScanSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class DshScannerService {
    private static final Path DEFAULT_DSH_ROOT = Path.of("F:\\deepseek-harness");
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", "node_modules", "dist", "lib", "target", "coverage", ".turbo", ".vite"
    );
    private static final Pattern CORDIS_ID_PATTERN = Pattern.compile("^(\\s*)-\\s+id:\\s*(.+?)\\s*$");
    private static final Pattern CORDIS_NAME_PATTERN = Pattern.compile("^\\s+name:\\s*(.+?)\\s*$");
    private static final Pattern CORDIS_DISABLED_PATTERN = Pattern.compile("^\\s+disabled:\\s*(.+?)\\s*$");

    private final Path dshRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DshScannerService() {
        this(resolveRootFromEnvironment());
    }

    public DshScannerService(Path dshRoot) {
        this.dshRoot = dshRoot;
    }

    public ScanResult scan() {
        Instant scannedAt = Instant.now();
        if (!Files.isDirectory(dshRoot)) {
            return new ScanResult(new ScanSummary(dshRoot.toString(), false, 0, 0, 0, 0, scannedAt), List.of(), List.of());
        }

        List<AgentPreset> presets = scanPresets();
        Map<String, AgentPreset> presetsById = new HashMap<>();
        for (AgentPreset preset : presets) {
            presetsById.put(preset.id(), preset);
        }

        List<Accessory> accessories = new ArrayList<>();
        accessories.addAll(scanCordisEntries(presetsById));
        accessories.addAll(scanPackages());

        long cordisEntryCount = accessories.stream()
                .filter(accessory -> "cordis_entry".equals(accessory.kind()))
                .filter(accessory -> "ok".equals(accessory.parseStatus()))
                .count();
        long packageCount = accessories.stream()
                .filter(accessory -> "package".equals(accessory.kind()))
                .filter(accessory -> "ok".equals(accessory.parseStatus()))
                .count();
        long parseFailureCount = accessories.stream()
                .filter(accessory -> "failed".equals(accessory.parseStatus()))
                .count();

        ScanSummary summary = new ScanSummary(
                dshRoot.toString(),
                true,
                presets.size(),
                Math.toIntExact(cordisEntryCount),
                Math.toIntExact(packageCount),
                Math.toIntExact(parseFailureCount),
                scannedAt
        );
        return new ScanResult(summary, accessories, presets);
    }

    private static Path resolveRootFromEnvironment() {
        String configured = System.getenv("DSH_REPO_ROOT");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_DSH_ROOT;
        }
        return Path.of(configured);
    }

    private List<AgentPreset> scanPresets() {
        Path presetRoot = dshRoot.resolve("apps/cli/config/agent-presets");
        if (!Files.isDirectory(presetRoot)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(presetRoot)) {
            return stream.filter(Files::isDirectory)
                    .map(this::readPreset)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(AgentPreset::order, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(AgentPreset::id))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private AgentPreset readPreset(Path presetDirectory) {
        String presetId = presetDirectory.getFileName().toString();
        Path presetFile = presetDirectory.resolve("preset.yml");
        if (!Files.isRegularFile(presetFile)) {
            return new AgentPreset(presetId, presetId, "", null, presetDirectory.toString());
        }
        try {
            List<String> lines = Files.readAllLines(presetFile);
            String name = firstYamlScalar(lines, "name", presetId);
            String description = firstYamlScalar(lines, "description", "");
            Integer order = parseInteger(firstYamlScalar(lines, "order", ""));
            return new AgentPreset(presetId, name, description, order, presetFile.toString());
        } catch (IOException e) {
            return new AgentPreset(presetId, presetId, "", null, presetFile.toString());
        }
    }

    private List<Accessory> scanCordisEntries(Map<String, AgentPreset> presetsById) {
        List<CordisFile> files = new ArrayList<>();
        Path presetRoot = dshRoot.resolve("apps/cli/config/agent-presets");
        if (Files.isDirectory(presetRoot)) {
            try (Stream<Path> stream = Files.list(presetRoot)) {
                stream.filter(Files::isDirectory).forEach(directory -> {
                    Path agentCordis = directory.resolve("agent.cordis.yml");
                    if (Files.isRegularFile(agentCordis)) {
                        String presetId = directory.getFileName().toString();
                        files.add(new CordisFile(agentCordis, "agent_preset", presetId));
                    }
                });
            } catch (IOException ignored) {
                // Missing preset directories simply produce no preset-backed entries.
            }
        }

        Path bundleRoot = dshRoot.resolve("packages/bundle");
        if (Files.isDirectory(bundleRoot)) {
            collectFiles(bundleRoot, path -> path.getFileName().toString().equals("cordis.patch.yml"))
                    .forEach(path -> files.add(new CordisFile(path, "bundle_patch", null)));
        }

        Path examples = dshRoot.resolve("examples");
        if (Files.isDirectory(examples)) {
            collectFiles(examples, path -> path.getFileName().toString().endsWith(".cordis.yml"))
                    .forEach(path -> files.add(new CordisFile(path, "example", null)));
        }

        return files.stream()
                .sorted(Comparator.comparing(file -> file.path().toString()))
                .flatMap(file -> parseCordisFile(file, presetsById).stream())
                .toList();
    }

    private List<Accessory> parseCordisFile(CordisFile file, Map<String, AgentPreset> presetsById) {
        try {
            List<String> lines = Files.readAllLines(file.path());
            String validationError = validateYamlShape(lines);
            if (validationError != null) {
                return List.of(Accessory.failedCordisFile(file.path().toString(), file.source(), validationError));
            }

            List<Accessory> entries = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                Matcher idMatcher = CORDIS_ID_PATTERN.matcher(lines.get(i));
                if (!idMatcher.matches()) {
                    continue;
                }
                String indent = idMatcher.group(1);
                String entryId = cleanScalar(idMatcher.group(2));
                int rowEnd = findRowEnd(lines, i + 1, indent.length());
                String backendName = findFirst(lines, i + 1, rowEnd, CORDIS_NAME_PATTERN);
                String disabledValue = findFirst(lines, i + 1, rowEnd, CORDIS_DISABLED_PATTERN);
                AgentPreset preset = file.presetId() == null ? null : presetsById.get(file.presetId());
                Boolean enabled = enabledFromDisabledValue(disabledValue);
                String parseMessage = parseMessageForDisabled(disabledValue);
                entries.add(Accessory.cordisEntry(
                        entryId,
                        backendName == null ? "" : backendName,
                        file.presetId(),
                        preset == null ? null : preset.name(),
                        enabled,
                        file.source(),
                        file.path().toString(),
                        "ok",
                        parseMessage
                ));
            }
            return entries;
        } catch (IOException e) {
            return List.of(Accessory.failedCordisFile(file.path().toString(), file.source(), e.getMessage()));
        }
    }

    private List<Accessory> scanPackages() {
        List<Path> packageFiles = new ArrayList<>();
        for (Path root : List.of(dshRoot.resolve("packages"), dshRoot.resolve("apps"))) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            packageFiles.addAll(collectFiles(root, path -> path.getFileName().toString().equals("package.json")));
        }

        return packageFiles.stream()
                .sorted()
                .map(this::readPackage)
                .filter(Objects::nonNull)
                .toList();
    }

    private Accessory readPackage(Path packageJson) {
        try {
            JsonNode json = objectMapper.readTree(packageJson.toFile());
            String name = text(json, "name");
            if (name.isBlank()) {
                return Accessory.failedPackage(packageJson.toString(), "package.json has no name");
            }
            if (!isDshPackageName(name)) {
                return null;
            }
            return Accessory.packageAccessory(name, text(json, "version"), text(json, "description"), packageJson.toString());
        } catch (IOException e) {
            return Accessory.failedPackage(packageJson.toString(), e.getMessage());
        }
    }

    private boolean isDshPackageName(String name) {
        return name.equals("@deepseek-ai/dsh") || name.startsWith("@deepseek-ai/dsh-");
    }

    private int findRowEnd(List<String> lines, int startIndex, int rowIndent) {
        for (int i = startIndex; i < lines.size(); i++) {
            Matcher idMatcher = CORDIS_ID_PATTERN.matcher(lines.get(i));
            if (idMatcher.matches() && idMatcher.group(1).length() <= rowIndent) {
                return i;
            }
        }
        return lines.size();
    }

    private String findFirst(List<String> lines, int start, int end, Pattern pattern) {
        for (int i = start; i < end; i++) {
            Matcher matcher = pattern.matcher(lines.get(i));
            if (matcher.matches()) {
                return cleanScalar(matcher.group(1));
            }
        }
        return null;
    }

    private static String validateYamlShape(List<String> lines) {
        int squareDepth = 0;
        for (String line : lines) {
            String stripped = stripComment(line);
            for (int i = 0; i < stripped.length(); i++) {
                char character = stripped.charAt(i);
                if (character == '[') {
                    squareDepth++;
                } else if (character == ']') {
                    squareDepth--;
                }
                if (squareDepth < 0) {
                    return "Unbalanced closing bracket";
                }
            }
        }
        return squareDepth == 0 ? null : "Unbalanced opening bracket";
    }

    private static String stripComment(String line) {
        int commentIndex = line.indexOf('#');
        return commentIndex >= 0 ? line.substring(0, commentIndex) : line;
    }

    private static Boolean enabledFromDisabledValue(String disabledValue) {
        if (disabledValue == null || disabledValue.isBlank()) {
            return true;
        }
        String normalized = disabledValue.toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return false;
        }
        if ("false".equals(normalized)) {
            return true;
        }
        return null;
    }

    private static String parseMessageForDisabled(String disabledValue) {
        if (disabledValue == null || disabledValue.isBlank()) {
            return "";
        }
        String normalized = disabledValue.toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "false".equals(normalized)) {
            return "";
        }
        return "disabled expression not evaluated: " + disabledValue;
    }

    private static String firstYamlScalar(List<String> lines, String key, String fallback) {
        Pattern pattern = Pattern.compile("^\\s*" + Pattern.quote(key) + ":\\s*(.*?)\\s*$");
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                return cleanScalar(matcher.group(1));
            }
        }
        return fallback;
    }

    private static String cleanScalar(String value) {
        String cleaned = stripComment(value).trim();
        if ((cleaned.startsWith("'") && cleaned.endsWith("'")) || (cleaned.startsWith("\"") && cleaned.endsWith("\""))) {
            return cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Path> collectFiles(Path root, Predicate<Path> matcher) {
        List<Path> matches = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && isIgnoredDirectory(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && matcher.test(file)) {
                        matches.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Root-level traversal failures are treated as an empty scan for that root.
        }
        return matches;
    }

    private boolean isIgnoredDirectory(Path dir) {
        Path fileName = dir.getFileName();
        return fileName != null && IGNORED_DIRECTORIES.contains(fileName.toString());
    }

    private String text(JsonNode json, String fieldName) {
        JsonNode value = json.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private record CordisFile(Path path, String source, String presetId) {
    }
}
