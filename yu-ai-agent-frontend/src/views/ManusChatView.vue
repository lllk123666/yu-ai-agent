<script setup>
import ChatRoom from '../components/ChatRoom.vue'
</script>

<template>
  <ChatRoom
    title="AI 超级智能体"
    subtitle="输入你想完成的任务，超级智能体会通过流式接口实时返回执行过程和结果。"
    mode="manus"
    bot-name="超级智能体"
    ai-avatar-src="/avatars/manus-ai.svg"
    welcome-message="你好，我是 AI 超级智能体。告诉我你的目标，我会尝试拆解任务并给出执行结果。"
    placeholder="例如：帮我分析一个产品方案，并列出执行计划。"
    chat-id-prefix="manus"
  />
</template>
