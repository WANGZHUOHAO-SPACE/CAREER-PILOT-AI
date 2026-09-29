<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const username = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)
const error = ref('')

async function submit(): Promise<void> {
  if (!username.value.trim() || !email.value.trim() || password.value.length < 8) {
    error.value = 'Enter a username, email and password of at least 8 characters.'; return
  }
  if (password.value !== confirmPassword.value) { error.value = 'Passwords do not match.'; return }
  loading.value = true
  error.value = ''
  try {
    await auth.register(username.value, email.value, password.value)
    ElMessage.success('Account created. Please sign in.')
    await router.replace('/login')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Registration failed.'
  } finally { loading.value = false }
}
</script>

<template>
  <main class="auth-page"><section class="auth-card">
    <span class="eyebrow">CAREERPILOT AI</span><h1>Create account</h1><p>Your jobs, conversations and documents stay in your workspace.</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <form @submit.prevent="submit">
      <label>Username<el-input v-model="username" autocomplete="username" maxlength="50" /></label>
      <label>Email<el-input v-model="email" type="email" autocomplete="email" maxlength="190" /></label>
      <label>Password<el-input v-model="password" type="password" autocomplete="new-password" show-password /></label>
      <label>Confirm password<el-input v-model="confirmPassword" type="password" autocomplete="new-password" show-password /></label>
      <el-button native-type="submit" type="primary" :loading="loading">Create account</el-button>
    </form>
    <p>Already registered? <RouterLink to="/login">Sign in</RouterLink></p>
  </section></main>
</template>
