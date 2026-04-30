<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { createLoveChatEventSource, createManusChatEventSource } from '../api/chat'
import { createChatId } from '../utils/chat'

const router = useRouter()

const props = defineProps({
  title: {
    type: String,
    required: true,
  },
  subtitle: {
    type: String,
    required: true,
  },
  mode: {
    type: String,
    required: true,
    validator: (value) => ['love', 'manus'].includes(value),
  },
  botName: {
    type: String,
    required: true,
  },
  aiAvatarSrc: {
    type: String,
    default: '',
  },
  welcomeMessage: {
    type: String,
    required: true,
  },
  placeholder: {
    type: String,
    default: '请输入你的问题',
  },
  chatIdPrefix: {
    type: String,
    default: 'chat',
  },
})

const chatId = ref('')
const inputMessage = ref('')
const messages = ref([])
const isStreaming = ref(false)
const errorMessage = ref('')
const eventSource = ref(null)
const messageListRef = ref(null)

const createWelcomeMessage = () => ({
  id: createChatId('assistant'),
  role: 'assistant',
  content: props.welcomeMessage,
})

const scrollToBottom = () => {
  nextTick(() => {
    if (!messageListRef.value) {
      return
    }

    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  })
}

const closeEventSource = () => {
  if (eventSource.value) {
    eventSource.value.close()
    eventSource.value = null
  }
}

const resetChat = () => {
  closeEventSource()
  chatId.value = createChatId(props.chatIdPrefix)
  inputMessage.value = ''
  errorMessage.value = ''
  isStreaming.value = false
  messages.value = [createWelcomeMessage()]
  scrollToBottom()
}

const sendMessage = () => {
  const text = inputMessage.value.trim()

  if (!text || isStreaming.value) {
    return
  }

  closeEventSource()
  errorMessage.value = ''
  inputMessage.value = ''
  isStreaming.value = true

  messages.value.push({
    id: createChatId('user'),
    role: 'user',
    content: text,
  })

  const isLoveMode = props.mode === 'love'
  const assistantIndex = isLoveMode ? messages.value.length : -1
  let manusStepCount = 0

  if (isLoveMode) {
    messages.value.push({
      id: createChatId('assistant'),
      role: 'assistant',
      content: '',
    })
  }

  scrollToBottom()

  const source = isLoveMode
    ? createLoveChatEventSource({ message: text, chatId: chatId.value })
    : createManusChatEventSource({ message: text })

  eventSource.value = source

  source.onmessage = (event) => {
    if (eventSource.value !== source) {
      return
    }

    if (isLoveMode) {
      if (!messages.value[assistantIndex]) {
        return
      }

      messages.value[assistantIndex].content += event.data
    } else {
      messages.value.push({
        id: createChatId('assistant'),
        role: 'assistant',
        content: `${event.data}\n`,
        step: manusStepCount + 1,
      })
      manusStepCount += 1
    }

    scrollToBottom()
  }

  source.onerror = () => {
    if (eventSource.value !== source) {
      return
    }

    const hasReply = isLoveMode
      ? Boolean(messages.value[assistantIndex]?.content.trim())
      : manusStepCount > 0

    closeEventSource()
    isStreaming.value = false

    if (!hasReply) {
      if (isLoveMode && messages.value[assistantIndex]) {
        messages.value[assistantIndex].content = '抱歉，服务暂时没有返回内容，请稍后重试。'
      } else {
        messages.value.push({
          id: createChatId('assistant'),
          role: 'assistant',
          content: '抱歉，服务暂时没有返回内容，请稍后重试。',
        })
      }

      errorMessage.value = '连接中断，请确认后端服务已启动。'
    }

    scrollToBottom()
  }
}

onMounted(() => {
  resetChat()
})

onBeforeUnmount(() => {
  closeEventSource()
})
</script>

<template>
  <section class="chat-page">
    <header class="chat-header">
      <button class="back-link" type="button" @click="router.push('/')">返回主页</button>
      <div class="chat-title-block">
        <p class="eyebrow">AI Application</p>
        <h1>{{ title }}</h1>
        <p>{{ subtitle }}</p>
      </div>
      <button class="ghost-button" type="button" @click="resetChat">新会话</button>
    </header>

    <div class="chat-shell">
      <div class="chat-meta">
        <span>聊天室 ID</span>
        <strong>{{ chatId }}</strong>
      </div>

      <div ref="messageListRef" class="message-list">
        <article
          v-for="message in messages"
          :key="message.id"
          class="message-row"
          :class="message.role"
        >
          <div class="avatar" :class="{ 'ai-avatar': message.role === 'assistant' }">
            <img v-if="message.role === 'user'" src="/avatars/user.svg" alt="用户头像" />
            <img
              v-else-if="message.role === 'assistant' && aiAvatarSrc"
              :src="aiAvatarSrc"
              :alt="`${botName}头像`"
            />
            <span v-else>{{ message.role === 'user' ? '我' : 'AI' }}</span>
          </div>
          <div class="message-card">
            <div class="message-name">
              {{ message.role === 'user' ? '你' : botName }}
              <span v-if="message.step"> · 步骤 {{ message.step }}</span>
            </div>
            <p class="message-content">
              {{ message.content || '正在思考中...' }}
            </p>
          </div>
        </article>
      </div>

      <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

      <form class="chat-form" @submit.prevent="sendMessage">
        <textarea
          v-model="inputMessage"
          :placeholder="placeholder"
          rows="2"
          :disabled="isStreaming"
          @keydown.enter.exact.prevent="sendMessage"
        />
        <button type="submit" :disabled="!inputMessage.trim() || isStreaming">
          {{ isStreaming ? '回复中...' : '发送' }}
        </button>
      </form>
    </div>
  </section>
</template>
