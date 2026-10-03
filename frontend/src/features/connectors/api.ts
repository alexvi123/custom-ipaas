import { useMutation, useQuery } from '@tanstack/react-query'
import { api, type TestActionResponse } from '../../api/client'

export class FieldErrors extends Error {
  readonly errors: Record<string, string>

  constructor(errors: Record<string, string>) {
    super('Some fields are invalid')
    this.errors = errors
  }
}

function problemDetail(body: unknown): string | undefined {
  if (typeof body === 'object' && body !== null && 'detail' in body && typeof body.detail === 'string') {
    return body.detail
  }
  return undefined
}

function fieldErrors(body: unknown): Record<string, string> | undefined {
  if (typeof body !== 'object' || body === null || !('errors' in body)) return undefined
  const errors = body.errors
  if (typeof errors !== 'object' || errors === null) return undefined
  return Object.fromEntries(Object.entries(errors).filter((entry): entry is [string, string] => typeof entry[1] === 'string'))
}

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
