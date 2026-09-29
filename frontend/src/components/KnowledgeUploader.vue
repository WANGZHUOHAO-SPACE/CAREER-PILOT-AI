<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { uploadKnowledge } from '@/api/knowledge'
import type { UploadResult } from '@/types/knowledge'

const emit = defineEmits<{ uploaded: [result: UploadResult] }>()
const input = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const dragging = ref(false)

async function handleFile(file?: File): Promise<void> {
  if (!file || uploading.value) return
  if (!/\.(pdf|txt|md)$/i.test(file.name)) {
    ElMessage.error('Choose a PDF, TXT or Markdown file.')
    return
  }
  if (file.size > 10 * 1024 * 1024 || file.size === 0) {
    ElMessage.error('File must be between 1 byte and 10 MB.')
    return
  }
  uploading.value = true
  try {
    const result = await uploadKnowledge(file)
    emit('uploaded', result)
    ElMessage.success(`${result.fileName} indexed`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Upload failed.')
  } finally {
    uploading.value = false
    if (input.value) input.value.value = ''
  }
}

function onDrop(event: DragEvent): void {
  dragging.value = false
  void handleFile(event.dataTransfer?.files[0])
}
</script>

<template>
  <div class="uploader" :class="{ 'uploader-dragging': dragging, 'uploader-busy': uploading }"
    @dragover.prevent="dragging = true" @dragleave.prevent="dragging = false" @drop.prevent="onDrop">
    <input ref="input" type="file" accept=".pdf,.txt,.md,application/pdf,text/plain,text/markdown" class="visually-hidden"
      aria-label="Choose a document" @change="handleFile(($event.target as HTMLInputElement).files?.[0])" />
    <div class="upload-symbol"><el-icon :size="30"><UploadFilled /></el-icon></div>
    <h3>{{ uploading ? 'Indexing your document...' : 'Drop a document here' }}</h3>
    <p>{{ uploading ? 'Parsing, chunking and creating embeddings. This may take a moment.' : 'PDF, TXT or MD · up to 10 MB' }}</p>
    <el-button :loading="uploading" :disabled="uploading" @click="input?.click()">{{ uploading ? 'Uploading...' : 'Choose file' }}</el-button>
  </div>
</template>
