import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FormProvider, useForm, useWatch } from 'react-hook-form'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { FieldDefinition } from '../../api/client'
import { jsonResponse } from '../../test/http'
import { DynamicSelect } from './DynamicSelect'
import type { FormValues } from './formSchema'

const chatField: FieldDefinition = {
  key: 'chatId',
  label: 'Chat',
  type: 'SELECT',
  required: true,
  options: [],
  dynamicOptions: true,
  allowCustomValue: true,
  dependsOn: [],
}

function Harness() {
  const form = useForm<FormValues>({ defaultValues: { chatId: '' } })
  const value = useWatch({ control: form.control, name: 'chatId' })
  return (
    <FormProvider {...form}>
      <DynamicSelect id="chat" connectorKey="telegram" actionKey="sendMessage" field={chatField} />
      <output aria-label="current value">{String(value ?? '')}</output>
    </FormProvider>
  )
}

function renderDynamicSelect(response: () => Response) {
  const fetchMock = vi.fn(async (request: Request) => {
    expect(new URL(request.url).pathname).toBe('/api/connectors/telegram/actions/sendMessage/fields/chatId/options')
    return response()
  })
  vi.stubGlobal('fetch', fetchMock)
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <Harness />
    </QueryClientProvider>,
  )
  return fetchMock
}

describe('DynamicSelect', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the options loaded from the connector', async () => {
    renderDynamicSelect(() => jsonResponse([{ value: '42', label: 'Alex · private' }], 200))

    expect(await screen.findByRole('option', { name: 'Alex · private' })).toBeInTheDocument()
  })

  it('loads the options again when Refresh is clicked', async () => {
    const fetchMock = renderDynamicSelect(() => jsonResponse([{ value: '42', label: 'Alex · private' }], 200))
    await screen.findByRole('option', { name: 'Alex · private' })

    await userEvent.click(screen.getByRole('button', { name: 'Refresh' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
  })

  it('says so when there are no options', async () => {
    renderDynamicSelect(() => jsonResponse([], 200))

    expect(await screen.findByText('No options found.')).toBeInTheDocument()
  })

  it('shows why options could not be loaded', async () => {
    renderDynamicSelect(() =>
      jsonResponse({ status: 502, detail: 'Telegram bot token not configured: set TELEGRAM_BOT_TOKEN' }, 502),
    )

    expect(await screen.findByText(/Telegram bot token not configured/)).toBeInTheDocument()
  })

  it('lets the user type a value manually', async () => {
    renderDynamicSelect(() => jsonResponse([], 200))

    await userEvent.click(await screen.findByRole('button', { name: 'Enter manually' }))
    await userEvent.type(screen.getByPlaceholderText('Type a value'), '12345')

    expect(screen.getByLabelText('current value')).toHaveTextContent('12345')
  })
})
