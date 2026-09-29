<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getApplications } from '@/api/applications'
import StatusTag from '@/components/StatusTag.vue'
import type { ApplicationStatus, ApplicationSummary } from '@/types/application'

const applications = ref<ApplicationSummary[]>([])
const filter = ref<ApplicationStatus | 'ALL'>('ALL')
const loading = ref(true)
const error = ref('')
const options: { label: string; value: ApplicationStatus | 'ALL' }[] = [
  { label: 'All', value: 'ALL' }, { label: 'Saved', value: 'SAVED' },
  { label: 'Applied', value: 'APPLIED' }, { label: 'Interview', value: 'INTERVIEW' },
  { label: 'Offer', value: 'OFFER' }, { label: 'Rejected', value: 'REJECTED' },
  { label: 'Withdrawn', value: 'WITHDRAWN' }
]

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleDateString() : '—'
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try { applications.value = await getApplications(filter.value === 'ALL' ? undefined : filter.value) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'Applications could not load.' }
  finally { loading.value = false }
}

watch(filter, () => { void load() })
onMounted(() => { void load() })
</script>

<template>
  <section class="standard-page">
    <div class="page-heading"><div><span class="eyebrow">YOUR PIPELINE</span><h1>Applications</h1><p>Keep each opportunity and its current status in view.</p></div>
      <el-button :icon="Refresh" :loading="loading" @click="load">Refresh</el-button></div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="state-alert" />
    <div class="panel table-panel"><div class="table-intro"><div><strong>{{ error ? 'Applications unavailable' : `${applications.length} records` }}</strong><span>Current application pipeline</span></div>
      <el-select v-model="filter" aria-label="Filter by application status" style="width: 170px"><el-option v-for="option in options" :key="option.value" :label="option.label" :value="option.value" /></el-select></div>
      <div v-if="error" class="load-error">Application data could not be loaded. Check your backend connection and try Refresh.</div>
      <el-table v-else v-loading="loading" :data="applications" stripe style="width: 100%" empty-text="No applications yet.">
        <el-table-column prop="company" label="Company" min-width="170"><template #default="scope">{{ scope.row.company || '—' }}</template></el-table-column>
        <el-table-column prop="position" label="Position" min-width="180"><template #default="scope">{{ scope.row.position || '—' }}</template></el-table-column>
        <el-table-column label="Status" min-width="125"><template #default="scope"><StatusTag :status="scope.row.status" /></template></el-table-column>
        <el-table-column label="Applied" min-width="125"><template #default="scope">{{ formatDate(scope.row.applyTime) }}</template></el-table-column>
        <el-table-column prop="remark" label="Remark" min-width="220" show-overflow-tooltip><template #default="scope">{{ scope.row.remark || '—' }}</template></el-table-column>
      </el-table>
      <div v-if="!loading && !applications.length && !error" class="table-cta">{{ filter === 'ALL' ? 'No applications yet. Save a role through the assistant to get started.' : 'No records with this status.' }} <RouterLink v-if="filter === 'ALL'" to="/chat">Open chat →</RouterLink></div>
    </div>
  </section>
</template>
