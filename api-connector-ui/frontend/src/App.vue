<template>
  <div class="layout">
    <div class="ambient" aria-hidden="true">
      <span class="orb orb-a" />
      <span class="orb orb-b" />
      <span class="grid-overlay" />
    </div>

    <header class="header">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path
              d="M16 2L28 9V23L16 30L4 23V9L16 2Z"
              stroke="currentColor"
              stroke-width="1.2"
            />
            <path d="M16 8L22 11.5V18.5L16 22L10 18.5V11.5L16 8Z" fill="currentColor" opacity="0.35" />
            <circle cx="16" cy="15" r="2.5" fill="currentColor" />
          </svg>
        </span>
        <div class="brand-text">
          <span class="brand-title">Integration Nexus</span>
          <span class="brand-sub">第三方对接控制台 · ITS</span>
        </div>
      </div>

      <nav class="nav">
        <router-link to="/connectors" class="nav-link">
          <span class="nav-dot" />
          连接器
        </router-link>
        <router-link to="/settings" class="nav-link">
          <span class="nav-dot" />
          设置
        </router-link>
        <a href="/swagger-ui.html" target="_blank" rel="noopener" class="nav-link nav-link--external">
          API 文档
        </a>
      </nav>
    </header>

    <main class="main">
      <router-view />
    </main>

    <footer class="footer">
      <span>Spec-driven invoke · Catalog registry · Legacy compat</span>
    </footer>
  </div>
</template>
