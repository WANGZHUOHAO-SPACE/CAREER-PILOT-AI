<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getRecentRequests, getToolStatistics, getTrace, getUsageSummary } from '@/api/observability'
import StatCard from '@/components/StatCard.vue'
import type { RecentRequests, ToolStatistics, TraceResponse, UsageRange, UsageSummary } from '@/types/observability'

const range = ref<UsageRange>('7d')
const route = useRoute()
const page = ref(1)
const sort = ref<'recent' | 'slowest'>('recent')
const summary = ref<UsageSummary | null>(null)
const recent = ref<RecentRequests | null>(null)
const tools = ref<ToolStatistics[]>([])
const trace = ref<TraceResponse | null>(null)
const drawerOpen = ref(false)
const loading = ref(false)
const traceLoading = ref(false)
const error = ref('')

const successRate = computed(() => summary.value?.totalRequests
  ? `${Math.round(summary.value.successfulRequests * 100 / summary.value.totalRequests)}%` : '—')

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [usage, requests, toolStats] = await Promise.all([
      getUsageSummary(range.value), getRecentRequests(page.value, 20, sort.value), getToolStatistics(range.value)
    ])
    summary.value = usage
    recent.value = requests
    tools.value = toolStats
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Observability data could not load.'
  } finally { loading.value = false }
}

async function openTrace(requestId: string): Promise<void> {
  traceLoading.value = true
  trace.value = null
  drawerOpen.value = true
  try { trace.value = await getTrace(requestId) }
  catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : 'Trace could not load.')
    drawerOpen.value = false
  } finally { traceLoading.value = false }
}

function formatTime(value: string): string { return new Date(value).toLocaleString() }
watch(range, () => { page.value = 1; void load() })
watch(sort, () => { page.value = 1; void load() })
onMounted(() => {
  void load()
  if (typeof route.query.requestId === 'string') void openTrace(route.query.requestId)
})
</script>

<template>
  <section class="standard-page observability-page">
    <div class="page-heading">
      <div><span class="eyebrow">AI OPERATIONS</span><h1>Observability</h1>
        <p>Usage, retrieval and tool activity for your account.</p></div>
      <el-select v-model="range" style="width: 135px" aria-label="Time range">
        <el-option label="Today" value="today" /><el-option label="Last 7 days" value="7d" />
        <el-option label="Last 30 days" value="30d" />
      </el-select>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="state-alert" />
    <div v-loading="loading" class="stat-grid">
      <StatCard label="AI Requests" :value="summary?.totalRequests ?? '—'" note="Selected period" icon="◎" tone="blue" />
      <StatCard label="Success Rate" :value="successRate" note="Completed requests" icon="✓" tone="teal" />
      <StatCard label="Total Tokens" :value="summary?.totalTokens ?? '—'" note="Provider reported" icon="◉" tone="violet" />
      <StatCard label="Avg Latency" :value="summary ? `${summary.averageLatencyMs} ms` : '—'" note="End to end" icon="↗" tone="amber" />
    </div>
    <div class="panel table-panel observability-table">
      <div class="table-intro"><div><strong>AI requests</strong><span>Select a request to view its execution trace.</span></div>
        <div class="observability-controls"><el-select v-model="sort" style="width: 130px" aria-label="Request sort">
          <el-option label="Most recent" value="recent" /><el-option label="Slowest" value="slowest" />
        </el-select><el-button text :loading="loading" @click="load">Refresh</el-button></div></div>
      <div v-if="!loading && !recent?.items.length" class="load-error">No AI requests recorded yet.</div>
      <el-table v-else :data="recent?.items ?? []" v-loading="loading" stripe>
        <el-table-column label="Time" min-width="155"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
        <el-table-column prop="model" label="Model" min-width="115" />
        <el-table-column label="Status" width="110"><template #default="{ row }"><el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'CANCELLED' ? 'info' : 'danger'" size="small">{{ row.status }}</el-tag></template></el-table-column>
        <el-table-column label="Tokens" width="85"><template #default="{ row }">{{ row.totalTokens ?? '—' }}</template></el-table-column>
        <el-table-column label="Latency" width="95"><template #default="{ row }">{{ row.latencyMs }} ms</template></el-table-column>
        <el-table-column prop="toolCallCount" label="Tools" width="75" />
        <el-table-column label="RAG" width="65"><template #default="{ row }">{{ row.ragUsed ? 'Yes' : 'No' }}</template></el-table-column>
        <el-table-column label="Request ID" min-width="150"><template #default="{ row }"><button class="trace-link" @click="openTrace(row.requestId)">{{ row.requestId.slice(0, 12) }}…</button></template></el-table-column>
      </el-table>
      <div class="observability-pagination"><el-button :disabled="page <= 1 || loading" @click="page--; load()">Previous</el-button>
        <span>Page {{ page }}</span><el-button :disabled="!recent?.hasMore || loading" @click="page++; load()">Next</el-button></div>
    </div>
    <div class="panel table-panel observability-table">
      <div class="table-intro"><div><strong>Tool activity</strong><span>Only tools invoked by your requests are shown.</span></div></div>
      <div v-if="!loading && !tools.length" class="load-error">No tool calls recorded in this period.</div>
      <el-table v-else :data="tools" v-loading="loading" stripe>
        <el-table-column prop="toolName" label="Tool" min-width="150" /><el-table-column prop="callCount" label="Calls" width="95" />
        <el-table-column prop="successCount" label="Successful" width="105" /><el-table-column prop="failureCount" label="Failed" width="95" />
        <el-table-column label="Avg Latency" width="130"><template #default="{ row }">{{ row.avgLatencyMs }} ms</template></el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="drawerOpen" title="Agent execution trace" size="min(560px, 100%)">
      <div v-loading="traceLoading" class="trace-drawer">
        <template v-if="trace">
          <p class="trace-id">{{ trace.request.requestId }}</p>
          <div class="trace-summary"><span>Model: {{ trace.request.model || 'Unknown' }}</span>
            <span>Tokens: {{ trace.tokenUsage.total ?? 'Unavailable' }}</span>
            <span>Latency: {{ trace.request.latencyMs }} ms</span>
            <span v-if="trace.request.streamDurationMs !== null">Stream: {{ trace.request.streamDurationMs }} ms · {{ trace.request.outputChunkCount }} chunks</span>
            <span v-if="trace.request.timeToFirstTokenMs !== null">First text: {{ trace.request.timeToFirstTokenMs }} ms</span>
            <span v-if="trace.errorType">Error: {{ trace.errorType }}</span></div>
          <el-timeline>
            <el-timeline-item v-for="(event, index) in trace.events" :key="index"
              :timestamp="formatTime(event.createdAt)" :type="event.status === 'FAILED' ? 'danger' : 'primary'">
              <strong>{{ event.name }}</strong><span class="trace-duration">{{ event.durationMs }} ms</span>
              <p v-if="event.type === 'RAG_SEARCH'">Top-K {{ event.topK }} · {{ event.chunkCount ?? '—' }} chunks retrieved</p>
              <p v-if="event.type === 'MEMORY'">{{ event.memoryMessageCount }} prior messages</p>
            </el-timeline-item>
          </el-timeline>
        </template>
      </div>
    </el-drawer>
  </section>
</template>
