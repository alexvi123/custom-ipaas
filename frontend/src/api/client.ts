import createClient from 'openapi-fetch'
import type { components, paths } from './schema'


export const api = createClient<paths>({
  baseUrl: window.location.origin,
  fetch: (request) => globalThis.fetch(request),
})

export type HealthReport = components['schemas']['HealthReport']
