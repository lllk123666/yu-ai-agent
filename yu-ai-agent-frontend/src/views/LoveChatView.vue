<script setup>
import ChatRoom from '../components/ChatRoom.vue'
</script>

<template>
  <ChatRoom
    title="AI 恋爱大师"
    subtitle="把你的情感困惑发给 AI，系统会通过 SSE 实时返回对话内容。"
    mode="love"
    bot-name="恋爱大师"
    ai-avatar-src="/avatars/love-ai.svg"
    welcome-message="你好，我是 AI 恋爱大师。可以和我说说你现在遇到的感情问题，我会尽量给你温和、具体的建议。"
    placeholder="例如：我和对象最近总是吵架，应该怎么沟通？"
    chat-id-prefix="love"
  />
</template>
