<script setup>
import { onMounted, ref } from 'vue';
import { listConnectorEndpoints } from '../api/http';

const props = defineProps({
  code3rd: { type: String, required: true },
});

const endpoints = ref([]);
const loading = ref(true);
const error = ref('');

onMounted(async () => {
  loading.value = true;
  error.value = '';
  try {
    endpoints.value = await listConnectorEndpoints(props.code3rd);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="catalog-endpoints">
    <h3>Catalog 端点目录（只读）</h3>
    <p class="hint">来自 Java Catalog / 运行时注册表，含 OpenAPI 分组与推荐 invoke URL。</p>
    <p v-if="loading">加载端点…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <table v-else-if="endpoints.length" class="table">
      <thead>
        <tr>
          <th>ID</th>
          <th>分组</th>
          <th>说明</th>
          <th>方法</th>
          <th>Path</th>
          <th>Query 参数</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="ep in endpoints" :key="ep.id">
          <td><code>{{ ep.id }}</code></td>
          <td>{{ ep.group || '—' }}</td>
          <td>{{ ep.summary || '—' }}</td>
          <td>{{ ep.method }}</td>
          <td><code>{{ ep.path }}</code></td>
          <td>
            <span v-if="!ep.parameters?.length">—</span>
            <span v-else>{{ ep.parameters.filter((p) => !p.in || p.in === 'query').map((p) => p.name).join(', ') }}</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="hint">无端点登记。</p>
  </div>
</template>
