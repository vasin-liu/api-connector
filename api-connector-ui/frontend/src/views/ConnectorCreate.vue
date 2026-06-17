<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import WizardSteps from '../components/WizardSteps.vue';
import EndpointsEditor from '../components/EndpointsEditor.vue';
import { createConnector, listProfiles } from '../api/http';

const router = useRouter();
const step = ref(1);
const profiles = ref([]);
const saving = ref(false);
const error = ref('');

const form = ref({
  code3rd: '',
  version: '1.0.0',
  baseUrl: 'https://',
  protocol: 'HTTP',
  auth: { type: 'none' },
  endpoints: [{ id: 'sample', method: 'GET', path: '/health', enabled: true }],
  response: { successWhen: '$.code==0', dataPath: '$.data' },
});

const credentialInputs = ref({ appId: '', appSecret: '', publicKey: '' });

function onProfileChange(profileId) {
  const meta = profiles.value.find((p) => p.profileId === profileId);
  form.value.auth = meta?.specFragmentExample
    ? { ...meta.specFragmentExample }
    : { type: profileId };
}

onMounted(async () => {
  profiles.value = await listProfiles();
});

async function submit(publish) {
  saving.value = true;
  error.value = '';
  try {
    const credPatch = {};
    for (const key of ['appId', 'appSecret', 'publicKey']) {
      if (credentialInputs.value[key]) credPatch[key] = credentialInputs.value[key];
    }
    await createConnector({
      spec: form.value,
      credentials: Object.keys(credPatch).length ? credPatch : null,
      publish,
    });
    router.push(`/connectors/${form.value.code3rd}`);
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section class="panel">
    <div class="page-header">
      <h1>注册新连接器</h1>
      <p class="subtitle">四步向导 · Spec 草稿可保存至本地库</p>
    </div>
    <WizardSteps :step="step" />
    <p v-if="error" class="error">{{ error }}</p>

    <div v-show="step === 1" class="form-grid">
      <label>编码 * <input v-model="form.code3rd" placeholder="DEMO_XXX" /></label>
      <label>版本 <input v-model="form.version" /></label>
      <label class="full">Base URL * <input v-model="form.baseUrl" /></label>
      <label>协议
        <select v-model="form.protocol">
          <option>HTTP</option>
          <option>HTTPS</option>
        </select>
      </label>
    </div>

    <div v-show="step === 2">
      <label>认证 Profile
        <select :value="form.auth.type" @change="onProfileChange($event.target.value)">
          <option v-for="p in profiles" :key="p.profileId" :value="p.profileId">
            {{ p.displayName }}
          </option>
        </select>
      </label>
      <div class="form-grid cred-grid">
        <label v-for="slot in profiles.find((p) => p.profileId === form.auth.type)?.credentialSlots || []"
               :key="slot.ref">
          {{ slot.label }}
          <input v-model="credentialInputs[slot.ref]" :type="slot.masked ? 'password' : 'text'" />
        </label>
      </div>
    </div>

    <div v-show="step === 3">
      <EndpointsEditor v-model="form.endpoints" />
      <div class="form-grid">
        <label class="full">successWhen <input v-model="form.response.successWhen" /></label>
        <label class="full">dataPath <input v-model="form.response.dataPath" /></label>
      </div>
    </div>

    <div class="actions">
      <button type="button" class="btn secondary" :disabled="step <= 1" @click="step--">上一步</button>
      <button type="button" class="btn secondary" :disabled="step >= 3" @click="step++">下一步</button>
      <button type="button" class="btn secondary" :disabled="saving" @click="submit(false)">保存草稿</button>
      <button type="button" class="btn" :disabled="saving" @click="submit(true)">发布</button>
    </div>
  </section>
</template>
