<template>
  <div class="layout">
    <header class="header">
      <div class="brand">ITS Integration Console</div>
      <nav class="nav">
        <router-link to="/connectors">连接器</router-link>
        <router-link to="/settings">设置</router-link>
        <a href="/swagger-ui.html" target="_blank" rel="noopener">API 文档</a>
      </nav>
    </header>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>
