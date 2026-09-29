<script setup lang="ts">
import type { ApplicationStatus } from '@/types/application'

const props = defineProps<{ status: ApplicationStatus }>()
const labels: Record<ApplicationStatus, string> = {
  SAVED: 'Saved', APPLIED: 'Applied', INTERVIEW: 'Interview',
  OFFER: 'Offer', REJECTED: 'Rejected', WITHDRAWN: 'Withdrawn'
}
const types: Record<ApplicationStatus, 'info' | 'primary' | 'warning' | 'success' | 'danger'> = {
  SAVED: 'info', APPLIED: 'primary', INTERVIEW: 'warning',
  OFFER: 'success', REJECTED: 'danger', WITHDRAWN: 'info'
}
</script>

<template>
  <el-tag :type="types[props.status]" effect="light" round>{{ labels[props.status] }}</el-tag>
</template>
