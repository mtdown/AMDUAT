import { fireEvent, render, screen, waitFor, within } from '@testing-library/vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';

const summary = {
  rootPath: 'F:\\deepseek-harness',
  found: true,
  presetCount: 4,
  cordisEntryCount: 2,
  packageCount: 1,
  parseFailureCount: 0,
  scannedAt: '2026-08-28T00:00:00Z',
};

const accessories = [
  {
    id: 'cordis-tool-pwsh',
    kind: 'cordis_entry',
    category: 'tool',
    displayName: '@deepseek-ai/dsh-tool-pwsh',
    backendName: '@deepseek-ai/dsh-tool-pwsh',
    entryId: 'tool-pwsh',
    presetId: 'standard',
    presetName: '标准模式',
    enabled: true,
    version: null,
    description: '',
    source: 'agent_preset',
    sourcePath: 'F:\\deepseek-harness\\apps\\cli\\config\\agent-presets\\standard\\agent.cordis.yml',
    parseStatus: 'ok',
    parseMessage: '',
  },
  {
    id: 'package-tool-pwsh',
    kind: 'package',
    category: 'tool',
    displayName: '@deepseek-ai/dsh-tool-pwsh',
    backendName: '@deepseek-ai/dsh-tool-pwsh',
    entryId: null,
    presetId: null,
    presetName: null,
    enabled: true,
    version: '0.1.0',
    description: 'Model-facing pwsh tool',
    source: 'package_json',
    sourcePath: 'F:\\deepseek-harness\\packages\\shell\\tool-pwsh\\package.json',
    parseStatus: 'ok',
    parseMessage: '',
  },
];

const presets = [
  {
    id: 'standard',
    name: '标准模式',
    description: '功能完整',
    order: 1,
    sourcePath: 'F:\\deepseek-harness\\apps\\cli\\config\\agent-presets\\standard\\preset.yml',
  },
];

describe('AMDUAT scan console', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn((url: string) => {
      if (url.endsWith('/api/scan/summary')) {
        return Promise.resolve(Response.json(summary));
      }
      if (url.endsWith('/api/accessories')) {
        return Promise.resolve(Response.json(accessories));
      }
      if (url.endsWith('/api/presets')) {
        return Promise.resolve(Response.json(presets));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders scan summary and accessory rows from the API', async () => {
    render(App);

    expect(await screen.findByText('配件扫描台')).toBeInTheDocument();
    expect(await screen.findByText('F:\\deepseek-harness')).toBeInTheDocument();
    expect(await screen.findByTestId('row-cordis-tool-pwsh')).toBeInTheDocument();
    expect(await screen.findByTestId('row-package-tool-pwsh')).toBeInTheDocument();
    expect(within(screen.getByTestId('row-cordis-tool-pwsh')).getByText('Cordis 行')).toBeInTheDocument();
    expect(within(screen.getByTestId('row-package-tool-pwsh')).getByText('Package')).toBeInTheDocument();
  });

  it('filters rows locally by search text and kind', async () => {
    render(App);

    await screen.findAllByText('@deepseek-ai/dsh-tool-pwsh');
    await fireEvent.update(screen.getByPlaceholderText('搜索名称、backend、路径'), 'package.json');
    await fireEvent.update(screen.getByLabelText('类型'), 'package');

    await waitFor(() => {
      expect(within(screen.getByTestId('row-package-tool-pwsh')).getByText('Package')).toBeInTheDocument();
      expect(screen.queryByTestId('row-cordis-tool-pwsh')).not.toBeInTheDocument();
    });
  });

  it('filters mounted rows by preset and opens the detail drawer', async () => {
    render(App);

    await screen.findByTestId('row-cordis-tool-pwsh');
    await fireEvent.update(screen.getByLabelText('Preset'), 'standard');

    await waitFor(() => {
      expect(screen.getByTestId('row-cordis-tool-pwsh')).toBeInTheDocument();
      expect(screen.queryByTestId('row-package-tool-pwsh')).not.toBeInTheDocument();
    });

    await fireEvent.click(screen.getByTestId('row-cordis-tool-pwsh'));

    const drawer = await screen.findByLabelText('配件详情');
    expect(drawer).toBeInTheDocument();
    expect(
      within(drawer).getByText('F:\\deepseek-harness\\apps\\cli\\config\\agent-presets\\standard\\agent.cordis.yml'),
    ).toBeInTheDocument();
  });

  it('shows a clear error when API loading fails', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('backend offline'))));

    render(App);

    expect(await screen.findByText(/无法加载扫描数据/)).toBeInTheDocument();
  });
});
