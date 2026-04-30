import request from './request'

const buildSseUrl = (url, params) =>
  request.getUri({
    url,
    method: 'GET',
    params,
  })

export const createLoveChatEventSource = ({ message, chatId }) =>
  new EventSource(
    buildSseUrl('/ai/love_app/chat/sse/emitter', {
      message,
      chatId,
    }),
  )

export const createManusChatEventSource = ({ message }) =>
  new EventSource(
    buildSseUrl('/ai/manus/chat', {
      message,
    }),
  )
