import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ActionDefinition, FieldDefinition } from '../../api/client'
import { jsonResponse } from '../../test/http'
import { ActionForm } from './ActionForm'

function field(overrides: Partial<FieldDefinition> & Pick<FieldDefinition, 'key' | 'label' | 'type'>): FieldDefinition {
  return { required: false, options: [], dynamicOptions: false, allowCustomValue: false, dependsOn: [], ...overrides }
}

// A made-up action that no real connector has: the form must render it purely from these definitions.
const action: ActionDefinition = {
  key: 'doThing',
  name: 'Do thing',
  description: 'Does a thing',
  fields: [
    field({ key: 'title', label: 'Title', type: 'STRING', required: true }),
    field({ key: 'notes', label: 'Notes', type: 'TEXT' }),
    field({ key: 'count', label: 'Count', type: 'NUMBER' }),
    field({ key: 'urgent', label: 'Urgent', type: 'BOOLEAN' }),
    field({
      key: 'colour',
      label: 'Colour',
      type: 'SELECT',
      required: true,
      defaultValue: 'red',
      options: [
        { value: 'red', label: 'Red' },
        { value: 'blue', label: 'Blue' },
      ],
    }),
    field({ key: 'payload', label: 'Payload', type: 'JSON' }),
    field({ key: 'target', label: 'Target', type: 'SELECT', dynamicOptions: true, allowCustomValue: true }),
  ],
}

function renderForm(testResponse: () => Response) {
  const fetchMock = vi.fn(async (request: Request) => {
    const { pathname } = new URL(request.url)
    if (pathname.endsWith('/fields/target/options')) return jsonResponse([{ value: 't1', label: 'Target 1' }], 200)
    if (pathname.endsWith('/actions/doThing/test')) return testResponse()
    return jsonResponse({ detail: 'unexpected request' }, 404)
  })
  vi.stubGlobal('fetch', fetchMock)
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ActionForm connectorKey="madeup" action={action} />
    </QueryClientProvider>,
  )
  return fetchMock
}

function testRequests(fetchMock: ReturnType<typeof renderForm>): Request[] {
  return fetchMock.mock.calls.map(([request]) => request).filter((request) => request.url.endsWith('/test'))
}

describe('ActionForm', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders a labelled input for every field definition', () => {
    renderForm(() => jsonResponse({}, 200))

    for (const { label } of action.fields) {
      expect(screen.getByLabelText(new RegExp(`^${label}`))).toBeInTheDocument()
    }
  })

  it('shows Required and sends nothing when required fields are empty', async () => {
    const fetchMock = renderForm(() => jsonResponse({}, 200))

    await userEvent.click(screen.getByRole('button', { name: 'Test step' }))

    expect(await screen.findByText('Required')).toBeInTheDocument()
    expect(testRequests(fetchMock)).toHaveLength(0)
  })

  it('sends the filled values and shows a successful result', async () => {
    const fetchMock = renderForm(() => jsonResponse({ success: true, output: { id: 1 }, durationMs: 12 }, 200))
    const user = userEvent.setup()

    await user.type(screen.getByLabelText(/^Title/), 'Hello')
    await user.type(screen.getByLabelText(/^Count/), '3')
    await user.click(screen.getByLabelText(/^Urgent/))
    await user.selectOptions(screen.getByLabelText(/^Colour/), 'blue')
    await user.click(screen.getByLabelText(/^Payload/))
    await user.paste('{"a":1}')
    await screen.findByRole('option', { name: 'Target 1' })
    await user.selectOptions(screen.getByLabelText(/^Target/), 't1')
    await user.click(screen.getByRole('button', { name: 'Test step' }))

    expect(await screen.findByText(/Success · 12 ms/)).toBeInTheDocument()
    const [request] = testRequests(fetchMock)
    expect(await request.json()).toEqual({
      values: { title: 'Hello', count: 3, urgent: true, colour: 'blue', payload: '{"a":1}', target: 't1' },
    })
  })

  it('shows the error when the external app rejected the test', async () => {
    renderForm(() => jsonResponse({ success: false, output: {}, error: 'Telegram: chat not found', durationMs: 80 }, 200))

    await userEvent.type(screen.getByLabelText(/^Title/), 'Hello')
    await userEvent.click(screen.getByRole('button', { name: 'Test step' }))

    expect(await screen.findByText('Telegram: chat not found')).toBeInTheDocument()
    expect(screen.getByText(/Failed · 80 ms/)).toBeInTheDocument()
  })

  it('shows field errors returned by the backend under the field', async () => {
    renderForm(() => jsonResponse({ status: 400, title: 'Invalid input', errors: { title: 'Too short' } }, 400))

    await userEvent.type(screen.getByLabelText(/^Title/), 'x')
    await userEvent.click(screen.getByRole('button', { name: 'Test step' }))

    expect(await screen.findByText('Too short')).toBeInTheDocument()
  })
})
