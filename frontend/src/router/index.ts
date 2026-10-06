import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),

  routes: [
    {
      path: '/',
      redirect: '/dashboard'
    },
    {
      path: '/login',
      component: () => import('@/views/LoginView.vue'),
      meta: {
        title: 'Sign in',
        public: true
      }
    },
    {
      path: '/register',
      component: () => import('@/views/RegisterView.vue'),
      meta: {
        title: 'Create account',
        public: true
      }
    },
    {
      path: '/dashboard',
      component: () => import('@/views/DashboardView.vue'),
      meta: {
        title: 'Dashboard'
      }
    },
    {
      path: '/chat',
      component: () => import('@/views/ChatView.vue'),
      meta: {
        title: 'AI Assistant'
      }
    },
    {
      path: '/knowledge',
      component: () => import('@/views/KnowledgeView.vue'),
      meta: {
        title: 'Knowledge Base'
      }
    },
    {
      path: '/jobs',
      component: () => import('@/views/JobsView.vue'),
      meta: {
        title: 'Jobs'
      }
    },
    {
      path: '/applications',
      component: () => import('@/views/ApplicationsView.vue'),
      meta: {
        title: 'Applications'
      }
    },
    {
      path: '/observability',
      component: () => import('@/views/ObservabilityView.vue'),
      meta: {
        title: 'Observability'
      }
    },
    {
      path: '/:pathMatch(.*)*',
      component: () => import('@/views/NotFoundView.vue'),
      meta: {
        title: 'Page not found'
      }
    }
  ],

  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async to => {
  const auth = useAuthStore()

  if (!auth.initialized) {
    await auth.initializeAuth()
  }

  if (to.meta.public) {
    if (auth.isAuthenticated) {
      const redirect =
        typeof to.query.redirect === 'string' &&
        to.query.redirect.startsWith('/') &&
        !to.query.redirect.startsWith('//')
          ? to.query.redirect
          : '/dashboard'

      return redirect
    }

    return true
  }

  if (!auth.isAuthenticated) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath
      }
    }
  }

  return true
})

router.afterEach(to => {
  if (typeof to.meta.title === 'string') {
    document.title = `${to.meta.title} | CareerPilot AI`
  }
})

export default router