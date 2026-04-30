export const createChatId = (prefix = 'chat') => {
  const timestamp = Date.now()
  const randomText = Math.random().toString(36).slice(2, 10)

  return `${prefix}-${timestamp}-${randomText}`
}
