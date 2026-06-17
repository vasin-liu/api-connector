<script setup>
import { onMounted, ref } from 'vue';
import { deleteConnector, listConnectors, reloadFromStore } from '../api/http';

const connectors = ref([]);
const loading = ref(true);
const error = ref('');
const message = ref('');
const syncing = ref(false);
const deleting = ref(null);

async function reload() {
  loading.value = true;
  error.value = '';
  try {
    connectors.value = await listConnectors();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function doSync() {
  syncing.value = true;
  message.value = '';
  error.value = '';
  try {
    const res = await reloadFromStore();
    message.value = `已从本地库加载 ${res.synced} 条已发布配置`;
    await reload();
  } catch (e) {
    error.value = e.message;
  } finally {
    syncing.value = false;
  }
}

async function doDelete(item) {
  if (item.catalogManaged) {
    error.value = `内置 Catalog 连接器 ${item.code3rd} 不可删除，请在代码中维护。`;
    return;
  }
  if (!window.confirm(`确定删除连接器 ${item.code3rd}？此操作不可恢复。`)) {
    return;
  }
  deleting.value = item.code3rd;
  error.value = '';
  message.value = '';
  try {
    await deleteConnector(item.code3rd);
    message.value = `已删除 ${item.code3rd}`;
    await reload();
  } catch (e) {
    error.value = e.message;
  } finally {
    deleting.value = null;
  }
}

onMounted(reload);
</script>

<template>
  <section class="panel">
    <div class="toolbar">
      <div>
        <h1>连接器矩阵</h1>
        <p class="hint">内置 Catalog 由 Java 注册；控制台配置持久化至本地 H2</p>
      </div>
      <div class="toolbar-actions">
        <button type="button" class="btn secondary" :disabled="syncing" @click="doSync">
          {{ syncing ? '同步中…' : '从库刷新' }}
        </button>
        <router-link class="btn" to="/connectors/new">+ 新建</router-link>
      </div>
    </div>

    <p v-if="message" class="ok">{{ message }}</p>
    <p v-if="loading" class="loading-pulse">正在扫描注册表…</p>
    <p v-else-if="error" class="error">{{ error }}</p>

    <div v-else-if="!connectors.length" class="empty-state">
      暂无已注册连接器 · 可新建或从库刷新
    </div>

    <div v-else class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>编码</th>
            <th>版本</th>
            <th>Base URL</th>
            <th>认证</th>
            <th>状态</th>
            <th>端点</th>
            <th>来源</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in connectors" :key="item.code3rd">
            <td><code>{{ item.code3rd }}</code></td>
            <td>{{ item.version }}</td>
            <td class="hint" style="max-width: 220px; overflow: hidden; text-overflow: ellipsis">{{ item.baseUrl }}</td>
            <td><code>{{ item.authType }}</code></td>
            <td><span class="badge">{{ item.specStatus || 'PUBLISHED' }}</span></td>
            <td><span class="badge badge--catalog">{{ item.endpointCount }}</span></td>
            <td>
              <span v-if="item.catalogManaged" class="badge badge--catalog">Catalog</span>
              <span v-else class="badge badge--warn">Custom</span>
            </td>
            <td class="row-actions">
              <router-link class="action-link" :to="`/connectors/${item.code3rd}`">编辑</router-link>
              <span class="action-sep">·</span>
              <router-link class="action-link" :to="`/trial/${item.code3rd}`">试调</router-link>
              <template v-if="!item.catalogManaged">
                <span class="action-sep">·</span>
                <button
                  type="button"
                  class="link danger"
                  :disabled="deleting === item.code3rd"
                  @click="doDelete(item)"
                >
                  删除
                </button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
