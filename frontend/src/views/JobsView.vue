<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh, View } from '@element-plus/icons-vue'
import { getJobs } from '@/api/jobs'
import type { JobSummary } from '@/types/job'

const jobs = ref<JobSummary[]>([])
const selected = ref<JobSummary | null>(null)
const loading = ref(true)
const error = ref('')

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleDateString() : '—'
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try { jobs.value = await getJobs() }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'Jobs could not load.' }
  finally { loading.value = false }
}

onMounted(() => { void load() })
</script>

<template>
  <section class="standard-page">
    <div class="page-heading"><div><span class="eyebrow">OPPORTUNITIES</span><h1>Saved Jobs</h1><p>Review roles that your assistant saved from your conversations.</p></div>
      <el-button :icon="Refresh" :loading="loading" @click="load">Refresh</el-button></div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="state-alert" />
    <div class="panel table-panel"><div class="table-intro"><strong>{{ error ? 'Jobs unavailable' : `${jobs.length} roles` }}</strong><span>Latest 200 · Saved through the AI Assistant</span></div>
      <div v-if="error" class="load-error">Job data could not be loaded. Check your backend connection and try Refresh.</div>
      <el-table v-else v-loading="loading" :data="jobs" stripe style="width: 100%" empty-text="No saved jobs yet.">
        <el-table-column prop="company" label="Company" min-width="170"><template #default="scope">{{ scope.row.company || '—' }}</template></el-table-column>
        <el-table-column prop="position" label="Position" min-width="180"><template #default="scope">{{ scope.row.position || '—' }}</template></el-table-column>
        <el-table-column prop="location" label="Location" min-width="130"><template #default="scope">{{ scope.row.location || '—' }}</template></el-table-column>
        <el-table-column prop="salary" label="Salary" min-width="120"><template #default="scope">{{ scope.row.salary || '—' }}</template></el-table-column>
        <el-table-column label="Created" min-width="130"><template #default="scope">{{ formatDate(scope.row.createTime) }}</template></el-table-column>
        <el-table-column label="Actions" width="110" fixed="right"><template #default="scope"><el-button text type="primary" :icon="View" @click="selected = scope.row">Details</el-button></template></el-table-column>
      </el-table>
      <div v-if="!loading && !jobs.length && !error" class="table-cta">Ask the assistant to save a job description you want to track. <RouterLink to="/chat">Open chat →</RouterLink></div>
    </div>
    <el-drawer :model-value="selected !== null" :title="selected?.position || 'Job details'" size="min(560px, 92vw)" @close="selected = null">
      <template v-if="selected"><dl class="detail-list"><div><dt>Company</dt><dd>{{ selected.company || '—' }}</dd></div><div><dt>Position</dt><dd>{{ selected.position || '—' }}</dd></div><div><dt>Location</dt><dd>{{ selected.location || '—' }}</dd></div><div><dt>Salary</dt><dd>{{ selected.salary || '—' }}</dd></div><div><dt>Saved</dt><dd>{{ formatDate(selected.createTime) }}</dd></div></dl>
        <h3 class="detail-subtitle">Job description</h3><p class="jd-text">{{ selected.jd }}</p></template>
    </el-drawer>
  </section>
</template>
