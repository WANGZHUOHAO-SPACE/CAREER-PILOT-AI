<script setup lang="ts">
import { useRoute } from 'vue-router'
import { ChatDotRound, Collection, DataAnalysis, Document, Briefcase, TrendCharts } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
function logout(): void { auth.logout(); void router.push('/login') }
const items = [
  { label: 'Dashboard', path: '/dashboard', icon: DataAnalysis },
  { label: 'AI Assistant', path: '/chat', icon: ChatDotRound },
  { label: 'Knowledge Base', path: '/knowledge', icon: Collection },
  { label: 'Jobs', path: '/jobs', icon: Briefcase },
  { label: 'Applications', path: '/applications', icon: Document },
  { label: 'Observability', path: '/observability', icon: TrendCharts }
]
</script>

<template>
  <aside class="sidebar">
    <RouterLink class="brand" to="/dashboard" aria-label="CareerPilot AI home">
      <span class="brand-mark">C<span class="brand-mark-accent">P</span></span>
      <span class="brand-copy"><strong>CareerPilot</strong><small>AI CAREER AGENT</small></span>
    </RouterLink>

    <div class="nav-label">WORKSPACE</div>
    <nav class="primary-nav" aria-label="Main navigation">
      <RouterLink v-for="item in items" :key="item.path" :to="item.path"
        class="nav-item" :class="{ 'nav-item-active': route.path === item.path }">
        <el-icon :size="19"><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>

    <div class="sidebar-bottom">
      <div class="sidebar-rule"></div>
      <span class="sidebar-bottom-dot"></span><span>{{ auth.user?.displayName || auth.user?.username }}</span>
      <button class="sidebar-logout" type="button" @click="logout">Logout</button>
    </div>
  </aside>
</template>
