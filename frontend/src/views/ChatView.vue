<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Delete, EditPen, Plus } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
import ChatInput from '@/components/ChatInput.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import type { Conversation } from '@/types/conversation'

const store = useChatStore()
const draft = ref('')
const messagesEnd = ref<HTMLElement | null>(null)
const suggestions = ['根据我的简历分析我适合什么岗位', '我的项目里使用过哪些技术？',
  '分析一下这个 Java 实习 JD', '根据我的经历生成 BOSS 打招呼话术']
onMounted(() => store.initialize())
async function send(): Promise<void> {
  if (!draft.value.trim() || store.busy) return
  const message = draft.value
  draft.value = ''
  await store.send(message)
}
async function newChat(): Promise<void> { await store.newChat(); draft.value = '' }
async function selectChat(id: string): Promise<void> { await store.selectChat(id); draft.value = '' }
async function rename(row: Conversation): Promise<void> {
  try {
    const result = await ElMessageBox.prompt('Give this conversation a title.', 'Rename conversation', {
      inputValue: row.title, inputValidator: value => !!value?.trim() && value.trim().length <= 100 || 'Use 1–100 characters.'
    })
    await store.rename(row.conversationId, result.value.trim())
  } catch { /* User cancelled. */ }
}
async function remove(row: Conversation): Promise<void> {
  try {
    await ElMessageBox.confirm('Delete this conversation and its saved messages?', 'Delete conversation',
      { type: 'warning', confirmButtonText: 'Delete' })
    await store.remove(row.conversationId)
  } catch { /* User cancelled. */ }
}
watch(() => [store.messages.length, store.messages.at(-1)?.content, store.loading, store.currentConversationId], async () => {
  await nextTick()
  messagesEnd.value?.scrollIntoView({ block: 'end' })
})
</script>

<template>
  <div class="chat-layout">
    <aside class="conversation-panel">
      <div class="conversation-header"><span class="eyebrow">CONVERSATIONS</span>
        <el-button type="primary" :icon="Plus" :disabled="store.busy" @click="newChat">New Chat</el-button></div>
      <div class="session-list" v-loading="store.listLoading">
        <p v-if="!store.conversations.length && !store.listLoading" class="conversation-empty">Start a new conversation.</p>
        <div v-for="row in store.conversations" :key="row.conversationId" class="conversation-row">
          <button type="button" class="session-item" :class="{ 'session-active': row.conversationId === store.currentConversationId }"
            :disabled="store.busy" @click="selectChat(row.conversationId)">
            <span class="session-dot"></span><span class="session-title">{{ row.title }}</span>
          </button>
          <div class="conversation-actions">
            <el-button text :icon="EditPen" :disabled="store.busy" aria-label="Rename conversation" @click="rename(row)" />
            <el-button text :icon="Delete" :disabled="store.busy" aria-label="Delete conversation" @click="remove(row)" />
          </div>
        </div>
        <el-button v-if="store.hasMoreConversations" text :disabled="store.busy" @click="store.loadMoreConversations">Load more</el-button>
      </div>
      <div class="conversation-footer"><span>Saved to your account</span>
        <code v-if="store.currentConversationId">{{ store.currentConversationId.slice(0, 8) }}</code></div>
    </aside>
    <section class="chat-panel" aria-label="AI conversation">
      <div class="chat-panel-header"><div><span class="assistant-orb">✦</span><span><strong>CareerPilot AI</strong>
        <small>Resume, job search and knowledge guidance</small></span></div><span class="chat-ready">Ready to help</span></div>
      <el-alert v-if="store.error" :title="store.error" type="error" show-icon :closable="false" />
      <div class="chat-scroll" role="log" aria-live="polite" v-loading="store.historyLoading">
        <div v-if="!store.messages.length" class="chat-welcome">
          <span class="welcome-orb">✦</span><span class="eyebrow">A CLEARER NEXT STEP</span>
          <h1>Start a new conversation</h1><p>Your history is saved to your account. Return to any conversation to continue.</p>
          <div class="suggestion-grid"><button v-for="question in suggestions" :key="question" type="button"
            @click="draft = question">{{ question }} <span>↗</span></button></div>
        </div>
        <div v-else class="message-list">
          <el-button v-if="store.hasOlderMessages" text :disabled="store.busy" @click="store.loadOlderMessages">Load earlier messages</el-button>
          <ChatMessage v-for="message in store.messages" :key="message.id" :message="message" />
          <div v-if="store.loading" class="processing-state"><span class="loading-dots"><i></i><i></i><i></i></span>
            {{ store.streamStatus || 'Thinking...' }}</div>
        </div>
        <div ref="messagesEnd"></div>
      </div>
      <div class="chat-bottom">
        <el-button v-if="store.loading" type="danger" plain :loading="store.stopping" :disabled="!store.activeRunId" @click="store.stop">Stop generating</el-button>
        <ChatInput v-model="draft" :disabled="store.busy" @send="send" />
        <p>Saved history and the model's recent context window are different. Answers may also use your indexed documents and tools.</p></div>
    </section>
  </div>
</template>

<style scoped>
.conversation-empty { padding: 12px; color: var(--muted); font-size: 12px; }
.conversation-row { display: flex; align-items: center; min-width: 0; }
.conversation-row .session-item { min-width: 0; flex: 1; }
.conversation-actions { display: flex; }
.conversation-actions .el-button { margin: 0; padding: 5px; }
</style>
