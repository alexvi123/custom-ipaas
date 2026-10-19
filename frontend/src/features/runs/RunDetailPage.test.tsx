import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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
      attempt: 1,
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
      attempt: 1,
      durationMs: 120,
    },
  ],
}

function renderRun(
  run: RunDetails,
  fetchMock = vi.fn<(request: Request) => Promise<Response>>(async () => jsonResponse(run, 200)),
) {
  vi.stubGlobal('fetch', fetchMock)
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
    expect(screen.queryByRole('button', { name: 'Retry run' })).not.toBeInTheDocument()
  })

  it('shows a step that waits for a retry with its attempt number', async () => {
    renderRun({
      ...failedRun,
      status: 'RUNNING',
      error: undefined,
      steps: [
        {
          ...failedRun.steps[0],
          status: 'WAITING_RETRY',
          attempt: 2,
          error: 'HTTP 503',
          nextRetryAt: '2026-10-04T10:00:30Z',
        },
      ],
    })

    expect(await screen.findByText('WAITING TO RETRY')).toBeInTheDocument()
    expect(screen.getByText('attempt 2')).toBeInTheDocument()
    expect(screen.getByText(/Next try at/)).toBeInTheDocument()
  })

  it('retries a failed run', async () => {
    const fetchMock = vi.fn(async (request: Request) =>
      request.method === 'POST' ? jsonResponse({ ...failedRun, status: 'PENDING' }, 202) : jsonResponse(failedRun, 200),
    )
    renderRun(failedRun, fetchMock)

    fireEvent.click(await screen.findByRole('button', { name: 'Retry run' }))

    await waitFor(() => {
      const posts = fetchMock.mock.calls.filter(([request]) => request.method === 'POST')
      expect(posts).toHaveLength(1)
      expect(new URL(posts[0][0].url).pathname).toBe('/api/runs/r1/retry')
    })
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
