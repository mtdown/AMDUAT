package com.amduat.scanner;

import com.amduat.scanner.model.Accessory;
import com.amduat.scanner.model.ScanResult;
import com.amduat.scanner.model.ScanSummary;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScannerControllerTest {
    @Test
    void healthReturnsOk() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ScannerController(new StubScannerService())).build();

        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));
    }

    @Test
    void accessoriesCanBeFilteredByQueryKindAndEnabled() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ScannerController(new StubScannerService())).build();

        mvc.perform(get("/api/accessories")
                        .param("q", "pwsh")
                        .param("kind", "cordis_entry")
                        .param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].entryId", hasItem("tool-pwsh")))
                .andExpect(jsonPath("$[*].kind", everyItem(is("cordis_entry"))))
                .andExpect(jsonPath("$[*].enabled", everyItem(is(true))));
    }

    @Test
    void accessoriesCanBeFilteredByCategorySourceAndPreset() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ScannerController(new StubScannerService())).build();

        mvc.perform(get("/api/accessories")
                        .param("category", "tool")
                        .param("source", "agent_preset")
                        .param("presetId", "standard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].entryId", hasItem("tool-pwsh")))
                .andExpect(jsonPath("$[*].category", everyItem(is("tool"))))
                .andExpect(jsonPath("$[*].source", everyItem(is("agent_preset"))))
                .andExpect(jsonPath("$[*].presetId", everyItem(is("standard"))));
    }

    @Test
    void presetsReturnsAgentPresetMetadata() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ScannerController(new StubScannerService())).build();

        mvc.perform(get("/api/presets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is("standard")))
                .andExpect(jsonPath("$[0].name", is("标准模式")));
    }

    @Test
    void summaryReturnsCountFields() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ScannerController(new StubScannerService())).build();

        mvc.perform(get("/api/scan/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found", is(true)))
                .andExpect(jsonPath("$.cordisEntryCount", is(2)))
                .andExpect(jsonPath("$.packageCount", is(1)))
                .andExpect(jsonPath("$.parseFailureCount", is(0)));
    }

    private static final class StubScannerService extends DshScannerService {
        StubScannerService() {
            super(java.nio.file.Path.of("unused"));
        }

        @Override
        public ScanResult scan() {
            List<Accessory> accessories = List.of(
                    Accessory.cordisEntry("tool-pwsh", "@deepseek-ai/dsh-tool-pwsh", "standard", "标准模式", true, "agent_preset", "F:\\deepseek-harness\\apps\\cli\\config\\agent-presets\\standard\\agent.cordis.yml", "ok", ""),
                    Accessory.cordisEntry("tool-web", "@deepseek-ai/dsh-tool-web", "standard", "标准模式", true, "agent_preset", "F:\\deepseek-harness\\apps\\cli\\config\\agent-presets\\standard\\agent.cordis.yml", "ok", ""),
                    Accessory.packageAccessory("@deepseek-ai/dsh-tool-pwsh", "0.1.0", "Model-facing pwsh tool", "F:\\deepseek-harness\\packages\\shell\\tool-pwsh\\package.json")
            );
            return new ScanResult(
                    new ScanSummary("F:\\deepseek-harness", true, 0, 2, 1, 0, Instant.parse("2026-08-28T00:00:00Z")),
                    accessories,
                    List.of(new com.amduat.scanner.model.AgentPreset("standard", "标准模式", "功能完整", 1, "F:\\deepseek-harness\\apps\\cli\\config\\agent-presets\\standard\\preset.yml"))
            );
        }
    }
}
