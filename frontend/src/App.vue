<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from '@/components/AppSidebar.vue'
import { request } from '@/api/request'

const route = useRoute()
const pageTitle = computed(() => String(route.meta.title || 'CareerPilot AI'))
const localTest = ref(false)
onMounted(async () => {
  try {
    const { data } = await request.get<{ mode: string }>('/api/health')
    localTest.value = data.mode === 'LOCAL_TEST'
  } catch { /* Existing page errors handle backend availability. */ }
})
</script>

<template>
  <div v-if="localTest" class="local-test-banner" role="status">LOCAL TEST MODE — deterministic integration fixtures, not a real AI model.</div>
  <RouterView v-if="route.meta.public" />
  <div v-else class="app-shell">
    <AppSidebar />
    <div class="app-main">
      <header class="topbar">
        <div class="topbar-title"><span class="topbar-eyebrow">CAREERPILOT AI / WORKSPACE</span><strong>{{ pageTitle }}</strong></div>
        <div class="topbar-agent"><span class="agent-light"></span>AI Career Agent</div>
      </header>
      <main class="page-content"><RouterView /></main>
    </div>
  </div>
</template>

<style scoped>
.local-test-banner { padding: 9px 18px; text-align: center; background: #fef3c7; color: #713f12; font-size: 13px; font-weight: 600; }
</style>
