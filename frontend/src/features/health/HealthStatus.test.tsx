import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse } from '../../test/http'
import { HealthStatus } from './HealthStatus'

function renderHealthStatus() {
  // A fresh client per test (no shared cache) and no retries, so failures show immediately.
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <HealthStatus />
    </QueryClientProvider>,
  )
}

describe('HealthStatus', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows OK when the backend and database are up', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 'UP', database: 'UP' }, 200)))

    renderHealthStatus()

    expect(await screen.findByText('backend: OK')).toBeInTheDocument()
  })

  it('shows DOWN when the backend reports the database is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 'DOWN', database: 'DOWN' }, 503)))

    renderHealthStatus()

    expect(await screen.findByText('backend: DOWN (database unreachable)')).toBeInTheDocument()
  })

  it('shows unreachable when the backend cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    renderHealthStatus()

    expect(await screen.findByText('backend: unreachable')).toBeInTheDocument()
  })
})
