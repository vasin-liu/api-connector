<script setup>
const endpoints = defineModel({ type: Array, required: true });
defineProps({ readonly: { type: Boolean, default: false } });

function addRow() {
  endpoints.value.push({
    id: `ep${endpoints.value.length + 1}`,
    method: 'GET',
    path: '/',
    enabled: true,
    bodyTemplate: null,
  });
}

function removeRow(index) {
  endpoints.value.splice(index, 1);
}
</script>

<template>
  <div>
    <button v-if="!readonly" type="button" class="btn secondary" @click="addRow">新增端点</button>
    <p v-else class="hint">端点由 Java Catalog 定义，此处只读。</p>
    <div v-if="endpoints.length" class="table-wrap">
      <table class="table">
      <thead>
        <tr>
          <th>启用</th>
          <th>ID</th>
          <th>方法</th>
          <th>路径</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(ep, idx) in endpoints" :key="idx">
          <td><input v-model="ep.enabled" type="checkbox" :disabled="readonly" /></td>
          <td><input v-model="ep.id" class="cell-input" :disabled="readonly" /></td>
          <td>
            <select v-model="ep.method" class="cell-input" :disabled="readonly">
              <option>GET</option>
              <option>POST</option>
              <option>PUT</option>
              <option>DELETE</option>
              <option>PATCH</option>
            </select>
          </td>
          <td><input v-model="ep.path" class="cell-input" :disabled="readonly" /></td>
          <td><button v-if="!readonly" type="button" class="link" @click="removeRow(idx)">删除</button></td>
        </tr>
      </tbody>
      </table>
    </div>
    <p v-else class="hint">暂无端点，试调时可手填 uri。</p>
  </div>
</template>
