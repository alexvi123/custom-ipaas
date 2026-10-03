export const starterDefinition = {
  trigger: { type: 'webhook' },
  steps: [
    {
      key: 'fetch',
      connector: 'http',
      action: 'request',
      inputs: { method: 'GET', url: 'https://httpbin.org/get?name={{trigger.body.name}}' },
    },
    {
      key: 'notify',
      connector: 'telegram',
      action: 'sendMessage',
      inputs: {
        chatId: 'YOUR_CHAT_ID',
        text: 'Hello {{trigger.body.name}}! The HTTP step returned status {{steps.fetch.output.status}}.',
      },
    },
  ],
}

export const starterText = JSON.stringify(starterDefinition, null, 2)
