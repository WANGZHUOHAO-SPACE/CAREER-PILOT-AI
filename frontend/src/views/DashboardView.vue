<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ArrowRight, Refresh } from '@element-plus/icons-vue'
import { getJobs } from '@/api/jobs'
import { getApplications } from '@/api/applications'
import { getKnowledgeStatus } from '@/api/knowledge'
import { getUsageSummary } from '@/api/observability'
import StatCard from '@/components/StatCard.vue'
import type { ApplicationSummary } from '@/types/application'
import type { JobSummary } from '@/types/job'
import type { KnowledgeStatus } from '@/types/knowledge'
import type { UsageSummary } from '@/types/observability'

const jobs = ref<JobSummary[] | null>(null)
const applications = ref<ApplicationSummary[] | null>(null)
const knowledge = ref<KnowledgeStatus | null>(null)
const aiUsage = ref<UsageSummary | null>(null)
const loading = ref(true)
const error = ref('')

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  const [jobResult, applicationResult, knowledgeResult, usageResult] = await Promise.allSettled([
    getJobs(), getApplications(), getKnowledgeStatus(), getUsageSummary('today')
  ])
  jobs.value = jobResult.status === 'fulfilled' ? jobResult.value : null
  applications.value = applicationResult.status === 'fulfilled' ? applicationResult.value : null
  knowledge.value = knowledgeResult.status === 'fulfilled' ? knowledgeResult.value : null
  aiUsage.value = usageResult.status === 'fulfilled' ? usageResult.value : null
  const failed = [jobResult, applicationResult, knowledgeResult, usageResult].filter(result => result.status === 'rejected')
  if (failed.length) error.value = 'Some dashboard data could not load. Check the backend and refresh.'
  loading.value = false
}

onMounted(() => { void load() })
</script>

<template>
  <section class="dashboard-page">
    <div class="dashboard-hero">
      <div>
        <span class="eyebrow light-eyebrow">YOUR CAREER WORKSPACE</span>
        <h1>Keep your next move in focus.</h1>
        <p>Track opportunities, organize your experience, and ask for guidance in one place.</p>
        <RouterLink to="/chat" class="hero-link">Open AI Assistant <el-icon><ArrowRight /></el-icon></RouterLink>
      </div>
      <div class="hero-trace" aria-hidden="true"><span>DISCOVER</span><i></i><span>PREPARE</span><i></i><span>APPLY</span></div>
    </div>

    <div class="section-heading"><div><span class="eyebrow">OVERVIEW</span><h2>At a glance</h2></div>
      <el-button text :icon="Refresh" :loading="loading" @click="load">Refresh</el-button></div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="state-alert" />
    <div v-loading="loading" class="stat-grid">
      <StatCard label="Saved Jobs" :value="jobs?.length ?? '—'" note="Latest 200 opportunities" icon="↗" tone="blue" />
      <StatCard label="Applications" :value="applications?.filter(item => item.status !== 'SAVED').length ?? '—'" note="Within latest 200 records" icon="◎" tone="teal" />
      <StatCard label="Interviews" :value="applications?.filter(item => item.status === 'INTERVIEW').length ?? '—'" note="Within latest 200 records" icon="◉" tone="violet" />
      <StatCard label="Knowledge Documents" :value="knowledge?.documentCount ?? '—'" note="Available to your AI assistant" icon="▤" tone="amber" />
    </div>

    <div class="section-heading"><div><span class="eyebrow">AI USAGE TODAY</span><h2>Your assistant activity</h2></div>
      <RouterLink class="text-link" to="/observability">View traces <el-icon><ArrowRight /></el-icon></RouterLink></div>
    <div v-loading="loading" class="stat-grid">
      <StatCard label="AI Requests" :value="aiUsage?.totalRequests ?? '—'" note="Today" icon="◎" tone="blue" />
      <StatCard label="Tokens" :value="aiUsage?.totalTokens ?? '—'" note="Provider reported" icon="◉" tone="violet" />
      <StatCard label="Avg Latency" :value="aiUsage ? `${aiUsage.averageLatencyMs} ms` : '—'" note="End to end" icon="↗" tone="amber" />
      <StatCard label="Success Rate" :value="aiUsage?.totalRequests ? `${Math.round(aiUsage.successfulRequests * 100 / aiUsage.totalRequests)}%` : '—'" note="Completed requests" icon="✓" tone="teal" />
    </div>

    <div class="dashboard-lower">
      <div class="panel next-step-panel">
        <span class="eyebrow">GET MOVING</span><h2>What would you like to do?</h2>
        <div class="action-list">
          <RouterLink to="/chat"><span class="action-badge">01</span><span><strong>Talk through a role</strong><small>Compare a job description with your resume</small></span><el-icon><ArrowRight /></el-icon></RouterLink>
          <RouterLink to="/knowledge"><span class="action-badge">02</span><span><strong>Add career materials</strong><small>Index a resume or project document</small></span><el-icon><ArrowRight /></el-icon></RouterLink>
          <RouterLink to="/applications"><span class="action-badge">03</span><span><strong>Review progress</strong><small>See where your applications stand</small></span><el-icon><ArrowRight /></el-icon></RouterLink>
        </div>
      </div>
      <div class="panel insight-panel"><span class="eyebrow">KNOWLEDGE BASE</span><h2>Grounded answers start here.</h2>
        <p>Your assistant can use indexed documents as context for questions about your projects and experience.</p>
        <div class="insight-count"><strong>{{ knowledge?.chunkCount ?? '—' }}</strong><span>indexed chunks</span></div>
        <RouterLink to="/knowledge" class="text-link">Manage documents <el-icon><ArrowRight /></el-icon></RouterLink>
      </div>
    </div>
  </section>
</template>
