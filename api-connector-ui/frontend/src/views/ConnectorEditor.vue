<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import WizardSteps from '../components/WizardSteps.vue';
import EndpointsEditor from '../components/EndpointsEditor.vue';
import CatalogEndpointsPanel from '../components/CatalogEndpointsPanel.vue';
import {
  getConnector,
  listProfiles,
  publishConnector,
  saveConnector,
} from '../api/http';

const props = defineProps({ code3rd: { type: String, required: true } });
const router = useRouter();

const step = ref(1);
const profiles = ref([]);
const loading = ref(true);
const saving = ref(false);
const error = ref('');
const message = ref('');
const specStatus = ref('PUBLISHED');
const catalogManaged = ref(false);

const form = ref({
  code3rd: '',
  version: '1.0.0',
  baseUrl: '',
  protocol: 'HTTP',
  auth: { type: 'none' },
  endpoints: [],
  response: { successWhen: '$.code==0', dataPath: '$.data' },
  transport: {},
});

const credentials = ref({ appId: '', appSecret: '', publicKey: '' });
const credentialInputs = ref({ appId: '', appSecret: '', publicKey: '' });

const selectedProfileId = computed({
  get: () => form.value.auth?.type || 'none',
  set: (id) => onProfileChange(id),
});

const activeProfile = computed(() =>
  profiles.value.find((p) => p.profileId === selectedProfileId.value),
);

function onProfileChange(profileId) {
  const meta = profiles.value.find((p) => p.profileId === profileId);
  const fragment = meta?.specFragmentExample
    ? { ...meta.specFragmentExample }
    : { type: profileId };
  form.value.auth = fragment;
}

function buildPayload(publish) {
  const credPatch = {};
  for (const key of ['appId', 'appSecret', 'publicKey']) {
    const val = credentialInputs.value[key];
    if (val != null && val !== '') {
      credPatch[key] = val;
    }
  }
  return {
    spec: { ...form.value },
    credentials: Object.keys(credPatch).length ? credPatch : null,
    publish,
  };
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const [config, profs] = await Promise.all([
      getConnector(props.code3rd),
      listProfiles(),
    ]);
    profiles.value = profs;
    form.value = { ...config.spec };
    if (!form.value.endpoints) form.value.endpoints = [];
    if (!form.value.response) form.value.response = {};
    if (!form.value.auth) form.value.auth = { type: 'none' };
    credentials.value = config.credentials || {};
    credentialInputs.value = { appId: '', appSecret: '', publicKey: '' };
    specStatus.value = config.specStatus;
    catalogManaged.value = !!config.catalogManaged;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function doSave(publish) {
  saving.value = true;
  error.value = '';
  message.value = '';
  try {
    const fn = publish ? publishConnector : saveConnector;
    const result = await fn(props.code3rd, buildPayload(publish));
    specStatus.value = result.specStatus;
    credentials.value = result.credentials;
    credentialInputs.value = { appId: '', appSecret: '', publicKey: '' };
    message.value = publish ? '已发布' : '草稿已保存';
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}

onMounted(load);
watch(() => props.code3rd, load);
</script>

<template>
  <section class="panel">
    <div class="toolbar">
      <div>
        <h1>{{ code3rd }}</h1>
        <div class="toolbar-meta">
          <span class="badge">{{ specStatus }}</span>
          <span v-if="catalogManaged" class="badge badge--catalog">Catalog Managed</span>
        </div>
      </div>
    </div>
    <p v-if="catalogManaged" class="hint">
      内置 Catalog：仅可改 Base URL 与凭证；端点/认证请在
      <code>api-connector-connectors</code> 维护后重启。
    </p>
    <WizardSteps :step="step" :code3rd="code3rd" />

    <p v-if="loading" class="loading-pulse">加载 Spec…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="ok">{{ message }}</p>

    <template v-if="!loading && !error">
      <div v-show="step === 1" class="form-grid">
        <label>编码 <input v-model="form.code3rd" disabled /></label>
        <label>版本 <input v-model="form.version" :disabled="catalogManaged" /></label>
        <label class="full">Base URL <input v-model="form.baseUrl" /></label>
        <label>协议
          <select v-model="form.protocol" :disabled="catalogManaged">
            <option>HTTP</option>
            <option>HTTPS</option>
            <option>WEBSOCKET</option>
          </select>
        </label>
      </div>

      <div v-show="step === 2">
        <label>认证 Profile
          <select v-model="selectedProfileId" :disabled="catalogManaged">
            <option v-for="p in profiles" :key="p.profileId" :value="p.profileId">
              {{ p.displayName }} ({{ p.profileId }})
            </option>
          </select>
        </label>
        <p class="hint">{{ activeProfile?.description }}</p>
        <div class="form-grid cred-grid">
          <template v-for="slot in activeProfile?.credentialSlots || []" :key="slot.ref">
            <label>
              {{ slot.label }}
              <input
                v-model="credentialInputs[slot.ref]"
                :type="slot.masked ? 'password' : 'text'"
                :placeholder="credentials[slot.ref] === '******' ? '留空不改' : ''"
              />
            </label>
          </template>
        </div>
        <p v-if="!activeProfile?.credentialSlots?.length" class="hint">此 Profile 无需凭证。</p>
      </div>

      <div v-show="step === 3">
        <CatalogEndpointsPanel v-if="catalogManaged" :code3rd="code3rd" />
        <EndpointsEditor v-model="form.endpoints" :readonly="catalogManaged" />
        <h3>响应映射</h3>
        <div class="form-grid">
          <label class="full">successWhen
            <input v-model="form.response.successWhen" :disabled="catalogManaged" />
          </label>
          <label class="full">dataPath
            <input v-model="form.response.dataPath" :disabled="catalogManaged" />
          </label>
        </div>
      </div>

      <div class="actions">
        <button type="button" class="btn secondary" :disabled="step <= 1" @click="step--">上一步</button>
        <button type="button" class="btn secondary" :disabled="step >= 3" @click="step++">下一步</button>
        <button type="button" class="btn secondary" :disabled="saving" @click="doSave(false)">保存草稿</button>
        <button type="button" class="btn" :disabled="saving" @click="doSave(true)">发布</button>
        <router-link class="btn secondary" :to="`/trial/${code3rd}`">去试调</router-link>
      </div>
    </template>
  </section>
</template>
