import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { title: 'Sign in', public: true } },
    { path: '/register', component: () => import('@/views/RegisterView.vue'), meta: { title: 'Create account', public: true } },
    { path: '/dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: 'Dashboard' } },
    { path: '/chat', component: () => import('@/views/ChatView.vue'), meta: { title: 'AI Assistant' } },
    { path: '/knowledge', component: () => import('@/views/KnowledgeView.vue'), meta: { title: 'Knowledge Base' } },
    { path: '/jobs', component: () => import('@/views/JobsView.vue'), meta: { title: 'Jobs' } },
    { path: '/applications', component: () => import('@/views/ApplicationsView.vue'), meta: { title: 'Applications' } },
    { path: '/observability', component: () => import('@/views/ObservabilityView.vue'), meta: { title: 'Observability' } },
    { path: '/:pathMatch(.*)*', component: () => import('@/views/NotFoundView.vue'), meta: { title: 'Page not found' } }
  ],
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async to => {
  const auth = useAuthStore()
  if (!auth.isAuthenticated) await auth.fetchCurrentUser()
  if (to.meta.public) return auth.isAuthenticated ? '/dashboard' : true
  return auth.isAuthenticated ? true : { path: '/login', query: { redirect: to.fullPath } }
})

export default router
