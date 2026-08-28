import type { Accessory, AgentPreset, ScanSummary } from './types';

async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<T>;
}

export async function loadScanData(): Promise<{ summary: ScanSummary; accessories: Accessory[]; presets: AgentPreset[] }> {
  const [summary, accessories, presets] = await Promise.all([
    fetchJson<ScanSummary>('/api/scan/summary'),
    fetchJson<Accessory[]>('/api/accessories'),
    fetchJson<AgentPreset[]>('/api/presets'),
  ]);
  return { summary, accessories, presets };
}
