import { useQuery } from '@tanstack/react-query'
import { api, type RunStatus } from '../../api/client'
import { problemDetail } from '../../api/problems'

/** Keep refreshing a run while it can still change. */
export function refetchWhileActive(status: RunStatus | undefined): number | false {
  return status === 'PENDING' || status === 'RUNNING' ? 2000 : false
}

export function useRuns(workflowId: string) {
  return useQuery({
    queryKey: ['runs', workflowId],
    refetchInterval: 3000,
    queryFn: async () => {
      const { data, response } = await api.GET('/api/runs', { params: { query: { workflowId } } })
      if (!data) throw new Error(`Could not load runs (${response.status})`)
      return data
    },
  })
}

export function useRun(id: string | undefined) {
  return useQuery({
    queryKey: ['run', id],
    enabled: id !== undefined,
    refetchInterval: (query) => refetchWhileActive(query.state.data?.status),
    queryFn: async () => {
      const { data, error, response } = await api.GET('/api/runs/{id}', { params: { path: { id: id ?? '' } } })
      if (!data) throw new Error(problemDetail(error) ?? `Could not load run (${response.status})`)
      return data
    },
  })
}
