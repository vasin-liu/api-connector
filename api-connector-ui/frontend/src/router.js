import { createRouter, createWebHistory } from 'vue-router';
import ConnectorList from './views/ConnectorList.vue';
import ConnectorCreate from './views/ConnectorCreate.vue';
import ConnectorEditor from './views/ConnectorEditor.vue';
import TrialPanel from './views/TrialPanel.vue';
import ConsoleSettings from './views/ConsoleSettings.vue';

const router = createRouter({
  history: createWebHistory('/console/'),
  routes: [
    { path: '/', redirect: '/connectors' },
    { path: '/connectors', name: 'connectors', component: ConnectorList },
    { path: '/connectors/new', name: 'connector-new', component: ConnectorCreate },
    { path: '/connectors/:code3rd', name: 'connector-edit', component: ConnectorEditor, props: true },
    { path: '/trial/:code3rd', name: 'trial', component: TrialPanel, props: true },
    { path: '/settings', name: 'settings', component: ConsoleSettings },
  ],
});

export default router;
