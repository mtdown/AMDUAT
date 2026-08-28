package com.amduat.scanner;

import com.amduat.scanner.model.Accessory;
import com.amduat.scanner.model.AgentPreset;
import com.amduat.scanner.model.ScanResult;
import com.amduat.scanner.model.ScanSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DshScannerServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void returnsEmptyScanWhenRootIsMissing() {
        DshScannerService scanner = new DshScannerService(tempDir.resolve("missing"));

        ScanResult result = scanner.scan();

        assertThat(result.summary().found()).isFalse();
        assertThat(result.summary().cordisEntryCount()).isZero();
        assertThat(result.summary().packageCount()).isZero();
        assertThat(result.accessories()).isEmpty();
        assertThat(result.presets()).isEmpty();
    }

    @Test
    void readsPresetMetadataAndAssociatesCordisEntriesWithoutExecutingJs() throws IOException {
        Path presetRoot = tempDir.resolve("apps/cli/config/agent-presets/standard");
        Files.createDirectories(presetRoot);
        Files.writeString(presetRoot.resolve("preset.yml"), """
                name: 标准模式
                description: 功能完整的编码 Agent
                order: 1
                """);
        Files.writeString(presetRoot.resolve("agent.cordis.yml"), """
                - id: tool-pwsh
                  name: '@deepseek-ai/dsh-tool-pwsh'
                  disabled: !!js process.platform !== 'win32'
                - id: persona
                  name: '@deepseek-ai/dsh-persona'
                """);

        ScanResult result = new DshScannerService(tempDir).scan();

        assertThat(result.presets())
                .extracting(AgentPreset::id, AgentPreset::name, AgentPreset::sourcePath)
                .containsExactly(tuple("standard", "标准模式", presetRoot.resolve("preset.yml").toString()));
        assertThat(result.accessories())
                .filteredOn(accessory -> accessory.entryId().equals("tool-pwsh"))
                .singleElement()
                .satisfies(accessory -> {
                    assertThat(accessory.kind()).isEqualTo("cordis_entry");
                    assertThat(accessory.category()).isEqualTo("tool");
                    assertThat(accessory.presetId()).isEqualTo("standard");
                    assertThat(accessory.presetName()).isEqualTo("标准模式");
                    assertThat(accessory.enabled()).isNull();
                    assertThat(accessory.parseStatus()).isEqualTo("ok");
                    assertThat(accessory.parseMessage()).contains("not evaluated");
                });
    }

    @Test
    void keepsScanningWhenOneCordisFileCannotBeParsed() throws IOException {
        Path examples = tempDir.resolve("examples/demo");
        Files.createDirectories(examples);
        Files.writeString(examples.resolve("bad.cordis.yml"), "- id: [\n");

        Path packageDir = tempDir.resolve("packages/shell/tool-pwsh");
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve("package.json"), """
                {
                  "name": "@deepseek-ai/dsh-tool-pwsh",
                  "version": "0.1.0",
                  "description": "Model-facing pwsh tool"
                }
                """);

        ScanResult result = new DshScannerService(tempDir).scan();

        assertThat(result.summary().parseFailureCount()).isEqualTo(1);
        assertThat(result.summary().packageCount()).isEqualTo(1);
        assertThat(result.accessories())
                .anySatisfy(accessory -> {
                    assertThat(accessory.kind()).isEqualTo("cordis_entry");
                    assertThat(accessory.parseStatus()).isEqualTo("failed");
                    assertThat(accessory.sourcePath()).endsWith("bad.cordis.yml");
                })
                .anySatisfy(accessory -> {
                    assertThat(accessory.kind()).isEqualTo("package");
                    assertThat(accessory.backendName()).isEqualTo("@deepseek-ai/dsh-tool-pwsh");
                    assertThat(accessory.category()).isEqualTo("tool");
                });
    }

    @Test
    void skipsIgnoredDirectoriesBeforeDescendingIntoThem() throws IOException {
        Path ignored = tempDir.resolve("examples/node_modules/deep/bad");
        Files.createDirectories(ignored);
        Files.writeString(ignored.resolve("ignored.cordis.yml"), "- id: [\n");

        Path valid = tempDir.resolve("examples/visible");
        Files.createDirectories(valid);
        Files.writeString(valid.resolve("tool.cordis.yml"), """
                - id: tool-web
                  name: '@deepseek-ai/dsh-tool-web'
                """);

        ScanResult result = new DshScannerService(tempDir).scan();

        assertThat(result.summary().parseFailureCount()).isZero();
        assertThat(result.accessories())
                .filteredOn(accessory -> "tool-web".equals(accessory.entryId()))
                .singleElement()
                .satisfies(accessory -> assertThat(accessory.sourcePath()).contains("examples"));
    }

    @Test
    void scansAllBundlePatchCordisFiles() throws IOException {
        writePatch("packages/bundle/base/cordis.patch.yml", "base-session", "@deepseek-ai/dsh-session");
        writePatch("packages/bundle/headless/cordis.patch.yml", "headless", "@deepseek-ai/dsh-headless");
        writePatch("packages/bundle/web-app/cordis.patch.yml", "plugin-inventory", "@deepseek-ai/dsh-host-plugin-inventory");

        ScanResult result = new DshScannerService(tempDir).scan();

        assertThat(result.accessories())
                .filteredOn(accessory -> "bundle_patch".equals(accessory.source()))
                .extracting(Accessory::entryId)
                .contains("base-session", "headless", "plugin-inventory");
    }

    @Test
    void excludesNonDshPackageJsonFiles() throws IOException {
        Path fixturePackage = tempDir.resolve("packages/typert/generator/tests/fixtures/type-model");
        Files.createDirectories(fixturePackage);
        Files.writeString(fixturePackage.resolve("package.json"), """
                {
                  "name": "@fixture/workspace",
                  "version": "1.0.0"
                }
                """);
        Path dshPackage = tempDir.resolve("packages/shell/tool-web");
        Files.createDirectories(dshPackage);
        Files.writeString(dshPackage.resolve("package.json"), """
                {
                  "name": "@deepseek-ai/dsh-tool-web",
                  "version": "1.0.0"
                }
                """);

        ScanResult result = new DshScannerService(tempDir).scan();

        assertThat(result.accessories())
                .filteredOn(accessory -> "package".equals(accessory.kind()))
                .extracting(Accessory::backendName)
                .containsExactly("@deepseek-ai/dsh-tool-web");
    }

    @Test
    void categorizesKnownDshPackages() {
        List<Accessory> accessories = List.of(
                packageAccessory("@deepseek-ai/dsh-tool-web", "packages/web/tool-web/package.json"),
                packageAccessory("@deepseek-ai/dsh-skill-filesystem", "packages/skill/skill-filesystem/package.json"),
                packageAccessory("@deepseek-ai/dsh-agent-presets", "packages/preset/agent-presets/package.json"),
                packageAccessory("@deepseek-ai/dsh-host-webserver", "packages/host/webserver/package.json"),
                packageAccessory("@deepseek-ai/dsh-client-ui-tool", "packages/client/ui-tool/package.json"),
                packageAccessory("@deepseek-ai/dsh-workflow-worker-thread", "packages/workflow/worker-thread/package.json"),
                packageAccessory("@deepseek-ai/dsh-persona", "packages/identity/persona/package.json"),
                packageAccessory("@deepseek-ai/dsh-web-app", "packages/bundle/web-app/package.json"),
                packageAccessory("@deepseek-ai/dsh", "apps/cli/package.json"),
                packageAccessory("@deepseek-ai/dsh-storage", "packages/storage/storage/package.json")
        );

        assertThat(accessories).extracting(Accessory::category)
                .containsExactly("tool", "skill", "agent_preset", "host", "client", "workflow", "prompt", "bundle", "app", "other");
    }

    private Accessory packageAccessory(String name, String sourcePath) {
        return Accessory.packageAccessory(name, "1.0.0", "", tempDir.resolve(sourcePath).toString());
    }

    private void writePatch(String relativePath, String entryId, String backendName) throws IOException {
        Path patch = tempDir.resolve(relativePath);
        Files.createDirectories(patch.getParent());
        Files.writeString(patch, """
                - id: %s
                  name: '%s'
                """.formatted(entryId, backendName));
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.api.Assertions.tuple(values);
    }
}
