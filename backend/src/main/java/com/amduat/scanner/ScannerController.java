package com.amduat.scanner;

import com.amduat.scanner.model.Accessory;
import com.amduat.scanner.model.AgentPreset;
import com.amduat.scanner.model.ScanSummary;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
public class ScannerController {
    private final DshScannerService scannerService;

    public ScannerController(DshScannerService scannerService) {
        this.scannerService = scannerService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/api/scan/summary")
    public ScanSummary summary() {
        return scannerService.scan().summary();
    }

    @GetMapping("/api/accessories")
    public List<Accessory> accessories(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String presetId
    ) {
        return scannerService.scan().accessories().stream()
                .filter(accessory -> matchesText(accessory, q))
                .filter(accessory -> matches(accessory.kind(), kind))
                .filter(accessory -> matches(accessory.category(), category))
                .filter(accessory -> matches(accessory.source(), source))
                .filter(accessory -> enabled == null || enabled.equals(accessory.enabled()))
                .filter(accessory -> matches(accessory.presetId(), presetId))
                .toList();
    }

    @GetMapping("/api/presets")
    public List<AgentPreset> presets() {
        return scannerService.scan().presets();
    }

    private boolean matches(String actual, String expected) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }

    private boolean matchesText(Accessory accessory, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return contains(accessory.displayName(), needle)
                || contains(accessory.backendName(), needle)
                || contains(accessory.entryId(), needle)
                || contains(accessory.description(), needle)
                || contains(accessory.sourcePath(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
