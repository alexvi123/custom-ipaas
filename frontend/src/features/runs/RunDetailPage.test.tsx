import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { RunDetails } from '../../api/client'
import { jsonResponse } from '../../test/http'
import { refetchWhileActive } from './api'
import { RunDetailPage } from './RunDetailPage'

const failedRun: RunDetails = {
  id: 'r1',
  workflowId: 'w1',
  workflowVersion: 2,
  status: 'FAILED',
  error: "Step 'notify' failed: Telegram: chat not found",
  trigger: { body: { name: 'Alex' }, query: {}, headers: {} },
  createdAt: '2026-10-04T10:00:00Z',
  startedAt: '2026-10-04T10:00:00Z',
  finishedAt: '2026-10-04T10:00:01Z',
  durationMs: 1000,
  steps: [
    {
      key: 'fetch',
      position: 0,
      connector: 'http',
      action: 'request',
      status: 'SUCCEEDED',
      input: { method: 'GET', url: 'https://httpbin.org/get' },
      output: { status: 200 },
      durationMs: 300,
    },
    {
      key: 'notify',
      position: 1,
      connector: 'telegram',
      action: 'sendMessage',
      status: 'FAILED',
      input: { chatId: '999', text: 'Hello Alex' },
      error: 'Telegram: chat not found',
      durationMs: 120,
    },
  ],
}

function renderRun(run: RunDetails) {
  vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(run, 200)))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/runs/r1']}>
        <Routes>
          <Route path="/runs/:id" element={<RunDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('RunDetailPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows every step with its status and the failed step error', async () => {
    renderRun(failedRun)

    expect(await screen.findByText('fetch')).toBeInTheDocument()
    expect(screen.getByText('notify')).toBeInTheDocument()
    expect(screen.getByText('SUCCEEDED')).toBeInTheDocument()
    expect(screen.getAllByText('FAILED')).toHaveLength(2) // run badge + step badge
    expect(screen.getByText('Telegram: chat not found')).toBeInTheDocument()
    expect(screen.getByText(/workflow version v2/)).toBeInTheDocument()
  })

  it('shows steps that have not run yet as PENDING', async () => {
    renderRun({
      ...failedRun,
      status: 'RUNNING',
      error: undefined,
      steps: failedRun.steps.map((step) => ({ ...step, status: 'PENDING', error: undefined, output: undefined })),
    })

    expect(await screen.findByText('RUNNING')).toBeInTheDocument()
    expect(screen.getAllByText('PENDING')).toHaveLength(2)
  })
})

describe('refetchWhileActive', () => {
  it('refreshes only while the run can still change', () => {
    expect(refetchWhileActive('PENDING')).toBe(2000)
    expect(refetchWhileActive('RUNNING')).toBe(2000)
    expect(refetchWhileActive('SUCCEEDED')).toBe(false)
    expect(refetchWhileActive('FAILED')).toBe(false)
  })
})
