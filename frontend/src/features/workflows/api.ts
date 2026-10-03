import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type WorkflowDetails } from '../../api/client'
import { FieldErrors, fieldErrors, problemDetail } from '../../api/problems'

export type SaveWorkflow = { name: string; definition: Record<string, unknown> }

export function useWorkflows() {
  return useQuery({
    queryKey: ['workflows'],
    queryFn: async () => {
      const { data, response } = await api.GET('/api/workflows')
      if (!data) throw new Error(`Could not load workflows (${response.status})`)
      return data
    },
  })
}

export function useWorkflow(id: string | undefined) {
  return useQuery({
    queryKey: ['workflow', id],
    enabled: id !== undefined,
    queryFn: async () => {
      const { data, error, response } = await api.GET('/api/workflows/{id}', { params: { path: { id: id ?? '' } } })
      if (!data) throw new Error(problemDetail(error) ?? `Could not load workflow (${response.status})`)
      return data
    },
  })
}

export function useSaveWorkflow(id: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (body: SaveWorkflow): Promise<WorkflowDetails> => {
      const { data, error, response } =
        id === undefined
          ? await api.POST('/api/workflows', { body })
          : await api.PUT('/api/workflows/{id}', { params: { path: { id } }, body })
      if (data) return data
      const errors = response.status === 400 ? fieldErrors(error) : undefined
      if (errors) throw new FieldErrors(errors)
      throw new Error(problemDetail(error) ?? `Could not save (${response.status})`)
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(['workflow', saved.id], saved)
      void queryClient.invalidateQueries({ queryKey: ['workflows'] })
    },
  })
}

export function useSetActive(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (active: boolean): Promise<WorkflowDetails> => {
      const params = { params: { path: { id } } }
      const { data, error, response } = active
        ? await api.POST('/api/workflows/{id}/activate', params)
        : await api.POST('/api/workflows/{id}/deactivate', params)
      if (!data) throw new Error(problemDetail(error) ?? `Could not change status (${response.status})`)
      return data
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(['workflow', saved.id], saved)
      void queryClient.invalidateQueries({ queryKey: ['workflows'] })
    },
  })
}
