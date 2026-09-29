<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Refresh } from '@element-plus/icons-vue'
import { clearKnowledge, getKnowledgeStatus } from '@/api/knowledge'
import KnowledgeUploader from '@/components/KnowledgeUploader.vue'
import type { KnowledgeStatus, UploadResult } from '@/types/knowledge'

const status = ref<KnowledgeStatus | null>(null)
const uploads = ref<(UploadResult & { localId: string })[]>([])
const loading = ref(true)
const clearing = ref(false)
const error = ref('')

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try { status.value = await getKnowledgeStatus() }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'Knowledge status could not load.' }
  finally { loading.value = false }
}

function uploaded(result: UploadResult): void {
  uploads.value.unshift({ ...result, localId: crypto.randomUUID() })
  void load()
}

async function clear(): Promise<void> {
  try {
    await ElMessageBox.confirm('Are you sure? This permanently removes your uploaded knowledge.', 'Clear Knowledge Base', { type: 'warning', confirmButtonText: 'Clear knowledge' })
  } catch { return }
  clearing.value = true
  try {
    status.value = await clearKnowledge()
    uploads.value = []
    ElMessage.success('Knowledge base cleared')
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : 'Could not clear the knowledge base.')
  } finally { clearing.value = false }
}

onMounted(() => { void load() })
</script>

<template>
  <section class="standard-page">
    <div class="page-heading"><div><span class="eyebrow">YOUR SOURCES</span><h1>Knowledge Base</h1><p>Give your assistant real context from resumes, projects and career notes.</p></div>
      <el-button :icon="Refresh" :loading="loading" @click="load">Refresh</el-button></div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="state-alert" />
    <div class="knowledge-grid">
      <div class="panel upload-panel"><div class="panel-heading"><span class="eyebrow">UPLOAD</span><h2>Add a document</h2><p>Documents are parsed into smaller sections and indexed for retrieval.</p></div>
        <KnowledgeUploader @uploaded="uploaded" /></div>
      <div class="panel knowledge-summary" v-loading="loading"><span class="eyebrow">INDEX STATUS</span><h2>Your library</h2>
        <div class="knowledge-metrics"><div><strong>{{ status?.documentCount ?? '—' }}</strong><span>Documents</span></div><div><strong>{{ status?.chunkCount ?? '—' }}</strong><span>Chunks</span></div></div>
        <div class="store-detail"><span>Vector store</span><code>{{ status?.vectorStoreType ?? '—' }}</code></div>
        <p class="memory-notice">Knowledge vectors are saved in PostgreSQL and remain available after a backend restart.</p>
        <el-button type="danger" plain :icon="Delete" :disabled="!status?.documentCount || loading" :loading="clearing" @click="clear">Clear Knowledge Base</el-button>
      </div>
    </div>
    <div class="panel recent-panel"><div class="section-heading"><div><span class="eyebrow">THIS BROWSER SESSION</span><h2>Recently indexed</h2></div></div>
      <el-empty v-if="!uploads.length" description="Upload your resume or project documents to build your career knowledge base." :image-size="88" />
      <div v-else class="upload-list"><div v-for="file in uploads" :key="file.localId" class="upload-row"><span class="file-mark">{{ file.fileName.split('.').pop()?.toUpperCase() }}</span>
        <div><strong>{{ file.fileName }}</strong><small>{{ file.chunks }} chunks</small></div><el-tag type="success" round>Indexed</el-tag></div></div>
      <p class="panel-footnote">The server exposes counts, not a file list. This list shows uploads from the current page session only.</p>
    </div>
  </section>
</template>
