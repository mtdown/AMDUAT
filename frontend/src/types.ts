export interface ScanSummary {
  rootPath: string;
  found: boolean;
  presetCount: number;
  cordisEntryCount: number;
  packageCount: number;
  parseFailureCount: number;
  scannedAt: string;
}

export interface Accessory {
  id: string;
  kind: 'cordis_entry' | 'package';
  category: string;
  displayName: string;
  backendName: string | null;
  entryId: string | null;
  presetId: string | null;
  presetName: string | null;
  enabled: boolean | null;
  version: string | null;
  description: string | null;
  source: string;
  sourcePath: string;
  parseStatus: 'ok' | 'failed';
  parseMessage: string;
}

export interface AgentPreset {
  id: string;
  name: string;
  description: string;
  order: number | null;
  sourcePath: string;
}
