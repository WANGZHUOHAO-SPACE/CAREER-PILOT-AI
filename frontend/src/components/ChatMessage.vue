<script setup lang="ts">
import type { ChatMessage } from '@/types/chat'

defineProps<{ message: ChatMessage }>()
</script>

<template>
  <div class="chat-message" :class="`chat-message-${message.role}`">
    <div class="message-avatar">{{ message.role === 'assistant' ? 'CP' : 'You' }}</div>
    <div class="message-body">
      <div class="message-meta">{{ message.role === 'assistant' ? 'CareerPilot AI' : 'You' }}</div>
      <div class="message-content">{{ message.content }}</div>
      <RouterLink v-if="message.requestId" class="message-trace"
        :to="{ path: '/observability', query: { requestId: message.requestId } }">Trace {{ message.requestId.slice(0, 8) }}…</RouterLink>
    </div>
  </div>
</template>
