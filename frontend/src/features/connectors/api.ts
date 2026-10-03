import { useMutation, useQuery } from '@tanstack/react-query'
import { api, type TestActionResponse } from '../../api/client'
import { FieldErrors, fieldErrors, problemDetail } from '../../api/problems'

export { FieldErrors }

export function useConnectors() {
  return useQuery({
    queryKey: ['connectors'],
    queryFn: async () => {
      const { data, response } = await api.GET('/api/connectors')
      if (!data) throw new Error(`Could not load connectors (${response.status})`)
      return data
    },
  })
}

export function useConnector(connectorKey: string | undefined) {
  return useQuery({
    queryKey: ['connector', connectorKey],
    enabled: connectorKey !== undefined,
    queryFn: async () => {
      const { data, error, response } = await api.GET('/api/connectors/{key}', {
        params: { path: { key: connectorKey ?? '' } },
      })
      if (!data) throw new Error(problemDetail(error) ?? `Could not load connector (${response.status})`)
      return data
    },
  })
}

/** Live options for a select field; `dependsOnValues` are the values of the fields it depends on. */
export function useFieldOptions(
  connectorKey: string,
  actionKey: string,
  fieldKey: string,
  dependsOnValues: Record<string, unknown>,
) {
  return useQuery({
    queryKey: ['options', connectorKey, actionKey, fieldKey, dependsOnValues],
    retry: false,
    queryFn: async () => {
      const { data, error, response } = await api.POST(
        '/api/connectors/{key}/actions/{action}/fields/{field}/options',
        {
          params: { path: { key: connectorKey, action: actionKey, field: fieldKey } },
          body: { values: dependsOnValues },
        },
      )
      if (!data) throw new Error(problemDetail(error) ?? `Could not load options (${response.status})`)
      return data
    },
  })
}

export function useTestAction(connectorKey: string, actionKey: string) {
  return useMutation({
    mutationFn: async (values: Record<string, unknown>): Promise<TestActionResponse> => {
      const { data, error, response } = await api.POST('/api/connectors/{key}/actions/{action}/test', {
        params: { path: { key: connectorKey, action: actionKey } },
        body: { values },
      })
      if (data) return data
      const errors = response.status === 400 ? fieldErrors(error) : undefined
      if (errors) throw new FieldErrors(errors)
      throw new Error(problemDetail(error) ?? `The test could not run (${response.status})`)
    },
  })
}
