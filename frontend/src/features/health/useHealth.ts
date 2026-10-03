import { useQuery } from '@tanstack/react-query'
import { api, type HealthReport } from '../../api/client'

async function fetchHealth(): Promise<HealthReport> {
  const { data, error, response } = await api.GET('/api/health')
  if (data) {
    return data // 200: backend and database up
  }
  if (response.status === 503 && error) {
    return error // 503: backend answered, but a dependency (the database) is down
  }
  // Anything else counts as unreachable.
  throw new Error(`Unexpected response from /api/health: ${response.status}`)
}

/** Polls the backend health every 10 seconds. Network failures surface as the query's error state. */
export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: fetchHealth,
    refetchInterval: 10_000,
    retry: false,
  })
}
