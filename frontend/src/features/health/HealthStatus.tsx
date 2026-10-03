import { useHealth } from './useHealth'

export function HealthStatus() {
  const health = useHealth()

  let text: string
  switch (health.status) {
    case 'pending':
      text = 'backend: checking…'
      break
    case 'error':
      text = 'backend: unreachable'
      break
    case 'success':
      text =
        health.data.status === 'UP'
          ? 'backend: OK'
          : `backend: DOWN (database ${health.data.database === 'DOWN' ? 'unreachable' : 'up'})`
      break
  }

  return <p role="status">{text}</p>
}
