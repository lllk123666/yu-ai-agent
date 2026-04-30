<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const apps = [
  {
    title: 'AI 恋爱大师',
    description: '面向情感咨询场景的实时聊天助手，支持根据会话 ID 保留上下文。',
    path: '/love',
    tag: 'Love App',
    avatar: '/avatars/love-ai.svg',
  },
  {
    title: 'AI 超级智能体',
    description: '面向复杂任务处理的智能体应用，实时展示 Manus 执行与思考结果。',
    path: '/manus',
    tag: 'Manus Agent',
    avatar: '/avatars/manus-ai.svg',
  },
]
</script>

<template>
  <main class="home-page">
    <section class="hero-card">
      <p class="eyebrow">Yu AI Agent</p>
      <h1>选择一个 AI 应用开始对话</h1>
      <p class="hero-desc">
        这里提供两个基于 SpringBoot SSE 接口的实时聊天应用，你可以在主页中快速切换。
      </p>
    </section>

    <section class="app-grid" aria-label="AI 应用列表">
      <button
        v-for="app in apps"
        :key="app.path"
        class="app-card"
        type="button"
        @click="router.push(app.path)"
      >
        <div class="app-card-top">
          <img :src="app.avatar" :alt="`${app.title}头像`" />
          <span>{{ app.tag }}</span>
        </div>
        <div>
          <h2>{{ app.title }}</h2>
          <p>{{ app.description }}</p>
        </div>
        <strong>进入应用</strong>
      </button>
    </section>
  </main>
</template>
