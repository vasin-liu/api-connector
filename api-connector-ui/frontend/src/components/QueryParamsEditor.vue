<script setup>
import { watch } from 'vue';

const rows = defineModel({ type: Array, required: true });

function addRow() {
  rows.value.push({ key: '', value: '' });
}

function removeRow(index) {
  rows.value.splice(index, 1);
}

watch(
  rows,
  (value) => {
    if (!value?.length) {
      rows.value = [{ key: '', value: '' }];
    }
  },
  { immediate: true },
);
</script>

<template>
  <div class="query-editor">
    <div class="query-editor-head">
      <h3>Query 参数</h3>
      <button type="button" class="btn secondary small" @click="addRow">新增</button>
    </div>
    <table class="table compact">
      <thead>
        <tr>
          <th>参数名</th>
          <th>值</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, idx) in rows" :key="idx">
          <td><input v-model="row.key" class="cell-input" placeholder="roadclid" /></td>
          <td><input v-model="row.value" class="cell-input" placeholder="值" /></td>
          <td>
            <button
              v-if="rows.length > 1"
              type="button"
              class="link"
              @click="removeRow(idx)"
            >
              删除
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
