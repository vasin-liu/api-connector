<script setup>
import { onMounted, ref, watch } from 'vue';
import QueryParamsEditor from '../components/QueryParamsEditor.vue';
import { getConnector, listConnectorEndpoints, trialProxy } from '../api/http';
import { rowsToQuery } from '../utils/queryParams';

const props = defineProps({ code3rd: { type: String, required: true } });

const endpointId = ref('');
const path = ref('/get');
const method = ref('GET');
const bodyText = ref('');
const queryRows = ref([{ key: '', value: '' }]);
const result = ref(null);
const error = ref('');
const loading = ref(false);
const endpoints = ref([]);
const useCustomPath = ref(false);

async function loadEndpoints() {
  try {
    const [config, catalog] = await Promise.all([
      getConnector(props.code3rd),
      listConnectorEndpoints(props.code3rd).catch(() => []),
    ]);
    if (catalog.length) {
      endpoints.value = catalog;
    } else {
      endpoints.value = (config.spec?.endpoints || []).filter((e) => e.enabled !== false);
    }
    if (endpoints.value.length) {
      applyEndpoint(endpoints.value[0].id);
    }
  } catch {
    // 试调仍可手填
  }
}

function applyEndpoint(id) {
  const ep = endpoints.value.find((e) => e.id === id);
  if (ep) {
    path.value = ep.path || '/';
    method.value = ep.method || 'GET';
    useCustomPath.value = false;
  }
}

function resolveBody() {
  if (!bodyText.value?.trim()) {
    return null;
  }
  const trimmed = bodyText.value.trim();
  try {
    JSON.parse(trimmed);
    return trimmed;
  } catch {
    return trimmed;
  }
}

async function runTrial() {
  loading.value = true;
  error.value = '';
  result.value = null;
  try {
    const query = rowsToQuery(queryRows.value);
    const body = resolveBody();
    let payload;
    if (endpointId.value && !useCustomPath.value) {
      payload = {
        endpointId: endpointId.value,
        query,
        headers: {},
        body,
      };
    } else {
      payload = {
        endpointId: endpointId.value || null,
        method: method.value || 'GET',
        path: path.value,
        query,
        headers: {},
        body,
      };
    }
    result.value = await trialProxy(props.code3rd, payload);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

onMounted(loadEndpoints);
watch(() => props.code3rd, loadEndpoints);
</script>

<template>
  <section class="panel">
    <h1>试调 · {{ code3rd }}</h1>
    <router-link :to="`/connectors/${code3rd}`">← 返回编辑</router-link>
    <div class="form-grid" style="margin-top: 1rem">
      <label v-if="endpoints.length" class="full">端点
        <select v-model="endpointId" @change="applyEndpoint(endpointId)">
          <option v-for="ep in endpoints" :key="ep.id" :value="ep.id">
            {{ ep.group ? ep.group + ' · ' : '' }}{{ ep.summary || ep.id }} ({{ ep.method }} {{ ep.path }})
          </option>
        </select>
      </label>
      <label class="full">
        <input v-model="useCustomPath" type="checkbox" /> 自定义 method + path（覆盖 Spec）
      </label>
      <template v-if="useCustomPath">
        <label>method
          <select v-model="method">
            <option>GET</option>
            <option>POST</option>
            <option>PUT</option>
            <option>DELETE</option>
            <option>PATCH</option>
          </select>
        </label>
        <label>path <input v-model="path" /></label>
      </template>
    </div>

    <QueryParamsEditor v-model="queryRows" />

    <label class="full" style="display: flex; flex-direction: column; gap: 0.25rem; margin-top: 0.75rem">
      body（JSON 字符串，POST 等场景）
      <textarea v-model="bodyText" rows="5" placeholder='{"messages":[{"role":"user","content":"你好"}]}' />
    </label>

    <div class="actions">
      <button class="btn" :disabled="loading" @click="runTrial">发送</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <pre v-if="result" class="code">{{ JSON.stringify(result, null, 2) }}</pre>
  </section>
</template>
