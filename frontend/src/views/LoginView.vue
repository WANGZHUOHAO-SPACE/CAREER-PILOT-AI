<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const email = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function submit(): Promise<void> {
  if (!email.value.trim() || !password.value) {
    error.value = 'Enter your email and password.'
    return
  }

  loading.value = true
  error.value = ''

  try {
    await auth.login(email.value.trim(), password.value)

    const redirect =
      typeof route.query.redirect === 'string' &&
      route.query.redirect.startsWith('/') &&
      !route.query.redirect.startsWith('//')
        ? route.query.redirect
        : '/dashboard'

    await router.replace(redirect)
  } catch (cause) {
    error.value =
      cause instanceof Error
        ? cause.message
        : 'Sign in failed.'
  } finally {
    loading.value = false
  }
}

if (route.query.expired) {
  ElMessage.warning('Session expired. Please sign in again.')
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-card">
      <span class="eyebrow">CAREERPILOT AI</span>

      <h1>Welcome back</h1>

      <p>
        Sign in to your private career workspace.
      </p>

      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
      />

      <form @submit.prevent="submit">
        <label>
          Email

          <el-input
            v-model="email"
            type="email"
            autocomplete="email"
            placeholder="you@example.com"
          />
        </label>

        <label>
          Password

          <el-input
            v-model="password"
            type="password"
            autocomplete="current-password"
            show-password
          />
        </label>

        <el-button
          native-type="submit"
          type="primary"
          :loading="loading"
        >
          Sign in
        </el-button>
      </form>

      <p>
        New here?
        <RouterLink to="/register">
          Create an account
        </RouterLink>
      </p>
    </section>
  </main>
</template>