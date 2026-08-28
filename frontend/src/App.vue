<template>
  <main class="shell">
    <section class="topbar">
      <div>
        <p class="eyebrow">AMDUAT</p>
        <h1>配件扫描台</h1>
      </div>
      <button class="icon-button" type="button" title="重新扫描" aria-label="重新扫描" @click="load">
        <RefreshCw :size="18" />
      </button>
    </section>

    <section v-if="error" class="error-band">
      <AlertTriangle :size="18" />
      <span>无法加载扫描数据：{{ error }}</span>
    </section>

    <section class="summary-grid" aria-label="扫描概览">
      <div class="metric wide">
        <span>DSH 路径</span>
        <strong>{{ summary?.rootPath ?? '等待扫描' }}</strong>
      </div>
      <div class="metric">
        <span>状态</span>
        <strong>{{ summary?.found ? '已找到' : '未找到' }}</strong>
      </div>
      <div class="metric">
        <span>Preset</span>
        <strong>{{ summary?.presetCount ?? 0 }}</strong>
      </div>
      <div class="metric">
        <span>Cordis 行</span>
        <strong>{{ summary?.cordisEntryCount ?? 0 }}</strong>
      </div>
      <div class="metric">
        <span>Package</span>
        <strong>{{ summary?.packageCount ?? 0 }}</strong>
      </div>
      <div class="metric">
        <span>解析失败</span>
        <strong>{{ summary?.parseFailureCount ?? 0 }}</strong>
      </div>
    </section>

    <section class="toolbar" aria-label="筛选">
      <label class="field">
        <span>搜索</span>
        <input v-model="filters.q" placeholder="搜索名称、backend、路径" type="search" />
      </label>
      <label class="field compact" for="kind-filter">
        <span>类型</span>
        <select id="kind-filter" v-model="filters.kind" aria-label="类型">
          <option value="">全部</option>
          <option value="cordis_entry">Cordis 行</option>
          <option value="package">Package</option>
        </select>
      </label>
      <label class="field compact" for="category-filter">
        <span>分类</span>
        <select id="category-filter" v-model="filters.category" aria-label="分类">
          <option value="">全部</option>
          <option v-for="category in categories" :key="category" :value="category">{{ categoryLabel(category) }}</option>
        </select>
      </label>
      <label class="field compact" for="source-filter">
        <span>来源</span>
        <select id="source-filter" v-model="filters.source" aria-label="来源">
          <option value="">全部</option>
          <option v-for="source in sources" :key="source" :value="source">{{ sourceLabel(source) }}</option>
        </select>
      </label>
      <label class="field compact" for="enabled-filter">
        <span>启用</span>
        <select id="enabled-filter" v-model="filters.enabled" aria-label="启用">
          <option value="">全部</option>
          <option value="true">启用</option>
          <option value="false">禁用</option>
          <option value="unknown">未知</option>
        </select>
      </label>
      <label class="field compact" for="preset-filter">
        <span>Preset</span>
        <select id="preset-filter" v-model="filters.presetId" aria-label="Preset">
          <option value="">全部</option>
          <option v-for="preset in presets" :key="preset.id" :value="preset.id">{{ preset.name }}</option>
        </select>
      </label>
    </section>

    <section class="table-wrap" aria-label="配件列表">
      <div class="table-head">
        <div>
          <span>配件</span>
          <strong>{{ filteredAccessories.length }}</strong>
        </div>
        <small v-if="summary">扫描于 {{ formatTime(summary.scannedAt) }}</small>
      </div>

      <div v-if="loading" class="empty-state">正在扫描...</div>
      <div v-else-if="filteredAccessories.length === 0" class="empty-state">没有匹配的配件</div>
      <table v-else>
        <thead>
          <tr>
            <th>名称</th>
            <th>类型</th>
            <th>分类</th>
            <th>启用</th>
            <th>Preset</th>
            <th>版本</th>
            <th>来源</th>
            <th>路径</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="accessory in filteredAccessories"
            :key="accessory.id"
            :data-testid="rowTestId(accessory)"
            :class="{ failed: accessory.parseStatus === 'failed' }"
            tabindex="0"
            @click="selected = accessory"
            @keydown.enter="selected = accessory"
          >
            <td>
              <div class="name-cell">
                <strong>{{ accessory.displayName }}</strong>
                <span>{{ accessorySubtitle(accessory) }}</span>
              </div>
            </td>
            <td><span class="pill neutral">{{ kindLabel(accessory.kind) }}</span></td>
            <td><span class="pill">{{ categoryLabel(accessory.category) }}</span></td>
            <td><span :class="['status', statusClass(accessory.enabled)]">{{ enabledLabel(accessory.enabled) }}</span></td>
            <td>{{ accessory.presetName ?? accessory.presetId ?? '-' }}</td>
            <td>{{ accessory.version ?? '-' }}</td>
            <td>{{ sourceLabel(accessory.source) }}</td>
            <td class="path-cell">{{ accessory.sourcePath }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <aside v-if="selected" class="drawer" aria-label="配件详情">
      <div class="drawer-panel">
        <button class="icon-button close" type="button" title="关闭" aria-label="关闭" @click="selected = null">
          <X :size="18" />
        </button>
        <p class="eyebrow">{{ kindLabel(selected.kind) }}</p>
        <h2>{{ selected.displayName }}</h2>
        <dl>
          <div><dt>backend</dt><dd>{{ selected.backendName ?? '-' }}</dd></div>
          <div><dt>entry id</dt><dd>{{ selected.entryId ?? '-' }}</dd></div>
          <div><dt>分类</dt><dd>{{ categoryLabel(selected.category) }}</dd></div>
          <div><dt>启用状态</dt><dd>{{ enabledLabel(selected.enabled) }}</dd></div>
          <div><dt>Preset</dt><dd>{{ selected.presetName ?? selected.presetId ?? '-' }}</dd></div>
          <div><dt>版本</dt><dd>{{ selected.version ?? '-' }}</dd></div>
          <div><dt>来源</dt><dd>{{ sourceLabel(selected.source) }}</dd></div>
          <div><dt>解析状态</dt><dd>{{ selected.parseStatus }} {{ selected.parseMessage }}</dd></div>
          <div><dt>描述</dt><dd>{{ selected.description || '-' }}</dd></div>
          <div><dt>来源路径</dt><dd class="detail-path">{{ selected.sourcePath }}</dd></div>
        </dl>
      </div>
    </aside>
  </main>
</template>

<script setup lang="ts">
import { AlertTriangle, RefreshCw, X } from 'lucide-vue-next';
import { computed, onMounted, reactive, ref } from 'vue';
import { loadScanData } from './api';
import type { Accessory, AgentPreset, ScanSummary } from './types';

const summary = ref<ScanSummary | null>(null);
const accessories = ref<Accessory[]>([]);
const presets = ref<AgentPreset[]>([]);
const selected = ref<Accessory | null>(null);
const loading = ref(false);
const error = ref('');
const filters = reactive({
  q: '',
  kind: '',
  category: '',
  source: '',
  enabled: '',
  presetId: '',
});

const categories = computed(() => unique(accessories.value.map((accessory) => accessory.category)));
const sources = computed(() => unique(accessories.value.map((accessory) => accessory.source)));
const filteredAccessories = computed(() => {
  const query = filters.q.trim().toLowerCase();
  return accessories.value.filter((accessory) => {
    return matchesQuery(accessory, query)
      && (!filters.kind || accessory.kind === filters.kind)
      && (!filters.category || accessory.category === filters.category)
      && (!filters.source || accessory.source === filters.source)
      && (!filters.presetId || accessory.presetId === filters.presetId)
      && matchesEnabled(accessory.enabled, filters.enabled);
  });
});

onMounted(load);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const data = await loadScanData();
    summary.value = data.summary;
    accessories.value = data.accessories;
    presets.value = data.presets;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause);
  } finally {
    loading.value = false;
  }
}

function matchesQuery(accessory: Accessory, query: string) {
  if (!query) {
    return true;
  }
  return [
    accessory.displayName,
    accessory.backendName,
    accessory.entryId,
    accessory.description,
    accessory.sourcePath,
  ].some((value) => value?.toLowerCase().includes(query));
}

function matchesEnabled(enabled: boolean | null, filter: string) {
  if (!filter) {
    return true;
  }
  if (filter === 'unknown') {
    return enabled === null;
  }
  return String(enabled) === filter;
}

function unique(values: string[]) {
  return [...new Set(values)].sort((left, right) => left.localeCompare(right));
}

function rowTestId(accessory: Accessory) {
  return `row-${accessory.kind === 'cordis_entry' ? 'cordis' : 'package'}-${accessory.entryId ?? slug(accessory.displayName)}`;
}

function accessorySubtitle(accessory: Accessory) {
  if (accessory.entryId) {
    return accessory.entryId;
  }
  return accessory.description || accessory.backendName || accessory.sourcePath;
}

function slug(value: string) {
  return value.replace(/^@deepseek-ai\/dsh-/, '').replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function kindLabel(kind: string) {
  return kind === 'cordis_entry' ? 'Cordis 行' : 'Package';
}

function categoryLabel(category: string) {
  const labels: Record<string, string> = {
    tool: '工具',
    skill: 'Skill',
    agent_preset: '智能体预设',
    host: '后端宿主',
    client: '前端客户端',
    workflow: '工作流',
    prompt: '提示词/身份',
    bundle: 'Bundle',
    app: 'App',
    other: '其他',
  };
  return labels[category] ?? category;
}

function sourceLabel(source: string) {
  const labels: Record<string, string> = {
    agent_preset: 'Agent Preset',
    bundle_patch: 'Bundle Patch',
    example: 'Example',
    package_json: 'package.json',
  };
  return labels[source] ?? source;
}

function enabledLabel(enabled: boolean | null) {
  if (enabled === null) {
    return '未知';
  }
  return enabled ? '启用' : '禁用';
}

function statusClass(enabled: boolean | null) {
  if (enabled === null) {
    return 'unknown';
  }
  return enabled ? 'enabled' : 'disabled';
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(value));
}
</script>
