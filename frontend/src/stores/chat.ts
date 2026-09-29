import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { streamChat, cancelRun } from '@/api/stream'
import * as api from '@/api/conversations'
import type { ChatMessage } from '@/types/chat'
import type { Conversation, ConversationMessage } from '@/types/conversation'

const selectedKey = (id: number) => `career-pilot-conversation-${id}`
const errorMessage = (error: unknown) => error instanceof Error ? error.message : 'Unable to load conversations. Please retry.'
const visible = (message: ConversationMessage): ChatMessage => ({
  id: String(message.id), role: message.role === 'USER' ? 'user' : 'assistant',
  content: message.content, createdAt: message.createdAt, requestId: message.requestId
})

export const useChatStore = defineStore('chat', () => {
  const userId = ref<number | null>(null)
  const currentConversationId = ref('')
  const conversations = ref<Conversation[]>([])
  const messages = ref<ChatMessage[]>([])
  const loading = ref(false)
  const historyLoading = ref(false)
  const listLoading = ref(false)
  const mutationLoading = ref(false)
  const error = ref('')
  const activeRunId = ref('')
  const streamStatus = ref('')
  const stopping = ref(false)
  let streamController: AbortController | null = null
  const hasMoreConversations = ref(false)
  const hasOlderMessages = ref(false)
  const busy = computed(() => loading.value || historyLoading.value || listLoading.value || mutationLoading.value)
  let listPage = 0
  let messagePage = 0
  let generation = 0

  function saveSelected(): void {
    try {
      if (userId.value !== null) localStorage.setItem(selectedKey(userId.value), currentConversationId.value)
    } catch { /* Server history remains available without local storage. */ }
  }

  function setUser(id: number | null): void {
    if (userId.value === id) return
    streamController?.abort()
    activeRunId.value = streamStatus.value = ''
    stopping.value = false
    generation++
    userId.value = id
    currentConversationId.value = ''
    conversations.value = []
    messages.value = []
    error.value = ''
    loading.value = historyLoading.value = listLoading.value = mutationLoading.value = false
    hasMoreConversations.value = hasOlderMessages.value = false
    listPage = messagePage = 0
    try {
      localStorage.removeItem('career-pilot-chat-v1')
      if (id !== null) {
        // Remove the old full-content cache. Persist only the selected server ID now.
        localStorage.removeItem(`career-pilot-chat-v2-${id}`)
        currentConversationId.value = localStorage.getItem(selectedKey(id)) || ''
      }
    } catch { /* Local storage is optional. */ }
  }

  async function refreshList(): Promise<void> {
    const snapshot = generation
    const page = await api.listConversations()
    if (snapshot !== generation) return
    conversations.value = page.items
    hasMoreConversations.value = page.hasMore
    listPage = 0
  }

  async function loadHistory(id: string): Promise<void> {
    const snapshot = generation
    historyLoading.value = true
    try {
      const result = await api.getMessages(id)
      if (snapshot !== generation) return
      currentConversationId.value = id
      messages.value = result.items.map(visible)
      hasOlderMessages.value = result.hasMore
      messagePage = 0
      saveSelected()
    } finally { if (snapshot === generation) historyLoading.value = false }
  }

  async function initialize(): Promise<void> {
    if (userId.value === null || busy.value) return
    const snapshot = generation
    listLoading.value = true
    error.value = ''
    try {
      await refreshList()
      if (snapshot !== generation) return
      const selected = conversations.value.find(row => row.conversationId === currentConversationId.value) ?? conversations.value[0]
      if (selected) await loadHistory(selected.conversationId)
      else { currentConversationId.value = ''; messages.value = []; saveSelected() }
    } catch (failure) { if (snapshot === generation) error.value = errorMessage(failure) }
    finally { if (snapshot === generation) listLoading.value = false }
  }

  async function newChat(): Promise<void> {
    if (userId.value === null || busy.value) return
    const snapshot = generation
    mutationLoading.value = true
    error.value = ''
    try {
      const conversation = await api.createConversation()
      if (snapshot !== generation) return
      currentConversationId.value = conversation.conversationId
      messages.value = []
      hasOlderMessages.value = false
      messagePage = 0
      saveSelected()
      await refreshList()
    } catch (failure) { if (snapshot === generation) error.value = errorMessage(failure) }
    finally { if (snapshot === generation) mutationLoading.value = false }
  }

  async function selectChat(id: string): Promise<void> {
    if (busy.value) return
    const snapshot = generation
    error.value = ''
    try { await loadHistory(id) } catch (failure) { if (snapshot === generation) error.value = errorMessage(failure) }
  }

  async function loadMoreConversations(): Promise<void> {
    if (busy.value || !hasMoreConversations.value) return
    const snapshot = generation
    listLoading.value = true
    try {
      const result = await api.listConversations(listPage + 1)
      if (snapshot !== generation) return
      const known = new Set(conversations.value.map(row => row.conversationId))
      conversations.value.push(...result.items.filter(row => !known.has(row.conversationId)))
      listPage = result.page
      hasMoreConversations.value = result.hasMore
    } catch (failure) { if (snapshot === generation) error.value = errorMessage(failure) }
    finally { if (snapshot === generation) listLoading.value = false }
  }

  async function loadOlderMessages(): Promise<void> {
    if (busy.value || !hasOlderMessages.value) return
    const snapshot = generation
    historyLoading.value = true
    try {
      const result = await api.getMessages(currentConversationId.value, messagePage + 1)
      if (snapshot !== generation) return
      const known = new Set(messages.value.map(row => row.id))
      messages.value = [...result.items.map(visible).filter(row => !known.has(row.id)), ...messages.value]
      messagePage = result.page
      hasOlderMessages.value = result.hasMore
    } catch (failure) { if (snapshot === generation) error.value = errorMessage(failure) }
    finally { if (snapshot === generation) historyLoading.value = false }
  }

  async function rename(id: string, title: string): Promise<void> {
    if (busy.value) return
    const snapshot = generation
    mutationLoading.value = true
    try {
      const row = await api.renameConversation(id, title)
      if (snapshot !== generation) return
      conversations.value = conversations.value.map(item => item.conversationId === id ? row : item)
    } catch (failure) { if (snapshot === generation) error.value = errorMessage(failure) }
    finally { if (snapshot === generation) mutationLoading.value = false }
  }

  async function remove(id: string): Promise<void> {
    if (busy.value) return
    const snapshot = generation
    mutationLoading.value = true
    try {
      await api.deleteConversation(id)
      if (snapshot !== generation) return
      await refreshList()
      if (currentConversationId.value === id) {
        currentConversationId.value = ''
        messages.value = []
        hasOlderMessages.value = false
        saveSelected()
        if (conversations.value[0]) await loadHistory(conversations.value[0].conversationId)
      }
    } catch (failure) { if (snapshot === generation) error.value = errorMessage(failure) }
    finally { if (snapshot === generation) mutationLoading.value = false }
  }

  async function send(message: string): Promise<void> {
    const trimmed = message.trim()
    if (!trimmed || trimmed.length > 8000 || busy.value || userId.value === null) return
    if (!currentConversationId.value) await newChat()
    if (!currentConversationId.value) return
    const snapshot = generation
    const id = currentConversationId.value
    loading.value = true
    error.value = ''
    messages.value.push({ id: `pending-${Date.now()}`, role: 'user', content: trimmed, createdAt: new Date().toISOString() })
    const assistantId = `stream-${Date.now()}`
    messages.value.push({ id: assistantId, role: 'assistant', content: '', createdAt: new Date().toISOString() })
    const controller = new AbortController()
    streamController = controller
    streamStatus.value = 'Thinking...'
    try {
      await streamChat({ conversationId: id, message: trimmed }, (name, event) => {
        if (snapshot !== generation || currentConversationId.value !== id) return
        activeRunId.value = event.runId
        if (name === 'rag.started') streamStatus.value = 'Searching knowledge base...'
        if (name === 'tool.started') streamStatus.value = `Using ${event.tool || 'tool'}...`
        if (name === 'llm.started' || name === 'token') streamStatus.value = 'Generating answer...'
        if (name === 'token' && typeof event.delta === 'string') {
          const assistant = messages.value.find(row => row.id === assistantId)
          if (assistant) { assistant.content += event.delta; assistant.requestId = event.requestId }
        }
        if (name === 'run.completed') streamStatus.value = event.status === 'CANCELLED' ? 'Generation stopped.' : ''
      }, controller.signal)
    }
    catch (failure) { if (snapshot === generation) error.value = errorMessage(failure) }
    finally {
      if (snapshot === generation) {
        try { await loadHistory(id); await refreshList() }
        catch (failure) { error.value = error.value || errorMessage(failure) }
        loading.value = false
        activeRunId.value = ''
        streamController = null
        stopping.value = false
      }
    }
  }

  async function stop(): Promise<void> {
    if (!activeRunId.value || stopping.value) return
    stopping.value = true
    try { await cancelRun(activeRunId.value) }
    catch (failure) { error.value = errorMessage(failure); stopping.value = false }
  }

  return { currentConversationId, conversations, messages, loading, historyLoading, listLoading, busy, error,
    hasMoreConversations, hasOlderMessages, setUser, initialize, newChat, selectChat, loadMoreConversations,
    loadOlderMessages, rename, remove, send, activeRunId, streamStatus, stopping, stop }
})
