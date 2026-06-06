<script setup>
import { onMounted, ref } from 'vue';
import { getConsoleInfo } from '../api/http';
import { loadClientSettings, saveClientSettings } from '../clientSettings';

const form = ref(loadClientSettings());
const message = ref('');
const consoleInfo = ref(null);
const infoError = ref('');

onMounted(async () => {
  try {
    consoleInfo.value = await getConsoleInfo();
    if (consoleInfo.value?.apiKeyHeader && !form.value.apiKeyHeader) {
      form.value.apiKeyHeader = consoleInfo.value.apiKeyHeader;
    }
  } catch (e) {
    infoError.value = e.message;
  }
});

function save() {
  saveClientSettings(form.value);
  message.value = '已保存到浏览器本地（localStorage），刷新后仍生效。';
}
</script>

<template>
  <section class="panel">
    <h1>控制台设置</h1>
    <p v-if="consoleInfo" class="hint">
      服务端鉴权：
      <strong>{{ consoleInfo.securityEnabled ? '已开启' : '未开启' }}</strong>
      · Header：<code>{{ consoleInfo.apiKeyHeader }}</code>
    </p>
    <p v-else-if="infoError" class="hint">
      无法读取服务端鉴权状态（{{ infoError }}）。若已开启 API Key，请先填写 Admin Key 后刷新本页。
    </p>
    <p class="hint">
      当服务端开启 <code>integration.security.enabled=true</code> 时，需在请求头携带 API Key。
      Admin API 与 Runtime API 可使用不同密钥。
    </p>
    <div class="form-grid" style="margin-top: 1rem">
      <label class="full">
        Header 名称
        <input v-model="form.apiKeyHeader" placeholder="X-Integration-Api-Key" />
      </label>
      <label class="full">
        Admin API Key
        <input v-model="form.adminApiKey" type="password" placeholder="对应 integration.security.admin-api-keys" />
      </label>
      <label class="full">
        Runtime API Key（可选）
        <input
          v-model="form.runtimeApiKey"
          type="password"
          placeholder="留空则 Runtime 请求复用 Admin Key"
        />
      </label>
    </div>
    <div class="actions">
      <button type="button" class="btn" @click="save">保存</button>
      <router-link class="btn secondary" to="/connectors">返回列表</router-link>
    </div>
    <p v-if="message" class="ok">{{ message }}</p>
  </section>
</template>
