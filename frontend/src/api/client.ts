import createClient from 'openapi-fetch'
import type { components, paths } from './schema'

export const api = createClient<paths>({
  baseUrl: window.location.origin,
  fetch: (request) => globalThis.fetch(request),
})

type Schemas = components['schemas']

export type HealthReport = Schemas['HealthReport']
export type ConnectorSummary = Schemas['ConnectorSummary']
export type ConnectorDetails = Schemas['ConnectorDetails']
export type ActionDefinition = Schemas['ActionDefinition']
export type FieldDefinition = Schemas['FieldDefinition']
export type Option = Schemas['Option']
export type TestActionResponse = Schemas['TestActionResponse']
