<script setup lang="ts">
import { ref, watch } from 'vue'
import { Promotion } from '@element-plus/icons-vue'

const props = defineProps<{ modelValue: string; disabled: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string]; send: [] }>()
const textarea = ref<HTMLTextAreaElement | null>(null)

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    if (!props.disabled && props.modelValue.trim()) emit('send')
  }
}

watch(() => props.disabled, disabled => {
  if (!disabled) textarea.value?.focus()
})
</script>

<template>
  <div class="chat-composer">
    <textarea ref="textarea" :value="modelValue" :disabled="disabled" rows="3" maxlength="8000"
      placeholder="Ask about your resume, a job, or your next step..."
      aria-label="Message CareerPilot AI" @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
      @keydown="onKeydown"></textarea>
    <div class="composer-footer">
      <span>Enter to send · Shift + Enter for a new line</span>
      <el-button type="primary" :icon="Promotion" :loading="disabled" :disabled="!modelValue.trim() || disabled" @click="emit('send')">Send</el-button>
    </div>
  </div>
</template>
