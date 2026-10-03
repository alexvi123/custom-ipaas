import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { WorkflowDetails } from '../../api/client'
import { jsonResponse } from '../../test/http'
import { WorkflowEditorPage } from './WorkflowEditorPage'

const saved: WorkflowDetails = {
  id: 'w1',
  name: 'My first workflow',
  active: false,
  version: 1,
  definition: { trigger: { type: 'webhook' }, steps: [] },
  webhookPath: '/hooks/abc123',
  createdAt: '2026-10-04T10:00:00Z',
  updatedAt: '2026-10-04T10:00:00Z',
}

type Handler = (request: Request) => Response

function renderEditor(createResponse: Handler) {
  const fetchMock = vi.fn(async (request: Request) => {
    const { pathname } = new URL(request.url)
    if (request.method === 'POST' && pathname === '/api/workflows') return createResponse(request)
    if (request.method === 'GET' && pathname === '/api/workflows/w1') return jsonResponse(saved, 200)
    if (request.method === 'GET' && pathname === '/api/runs') return jsonResponse([], 200)
    return jsonResponse({ detail: `unexpected ${request.method} ${pathname}` }, 404)
  })
  vi.stubGlobal('fetch', fetchMock)
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/workflows/new']}>
        <Routes>
          <Route path="/workflows/new" element={<WorkflowEditorPage />} />
          <Route path="/workflows/:id" element={<WorkflowEditorPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return fetchMock
}

function creates(fetchMock: ReturnType<typeof renderEditor>) {
  return fetchMock.mock.calls.filter(([request]) => request.method === 'POST')
}

describe('WorkflowEditorPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('starts from the starter template', () => {
    renderEditor(() => jsonResponse(saved, 201))

    const definition = screen.getByLabelText<HTMLTextAreaElement>('Definition (JSON)')
    expect(definition.value).toContain('"connector": "telegram"')
  })

  it('does not send invalid JSON to the backend', async () => {
    const fetchMock = renderEditor(() => jsonResponse(saved, 201))
    const user = userEvent.setup()

    await user.clear(screen.getByLabelText('Definition (JSON)'))
    await user.click(screen.getByLabelText('Definition (JSON)'))
    await user.paste('{oops')
    await user.click(screen.getByRole('button', { name: 'Create workflow' }))

    expect(screen.getByText(/Not valid JSON/)).toBeInTheDocument()
    expect(creates(fetchMock)).toHaveLength(0)
  })

  it('lists the problems the backend found, per JSON path', async () => {
    renderEditor(() =>
      jsonResponse({ status: 400, title: 'Invalid workflow', errors: { 'steps[1].inputs.chatId': 'Required' } }, 400),
    )

    await userEvent.click(screen.getByRole('button', { name: 'Create workflow' }))

    expect(await screen.findByText('steps[1].inputs.chatId')).toBeInTheDocument()
    expect(screen.getByText(/Required/)).toBeInTheDocument()
  })

  it('opens the saved workflow with its webhook URL', async () => {
    const fetchMock = renderEditor(() => jsonResponse(saved, 201))

    await userEvent.click(screen.getByRole('button', { name: 'Create workflow' }))

    expect(await screen.findByText(`${window.location.origin}/hooks/abc123`)).toBeInTheDocument()
    expect(await screen.findByText(/No runs yet/)).toBeInTheDocument()
    const [[request]] = creates(fetchMock)
    const body = await request.json()
    expect(body.name).toBe('My first workflow')
    expect(body.definition.trigger).toEqual({ type: 'webhook' })
  })
})
