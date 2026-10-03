import { useState } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { cn } from '@/lib/utils'
import { ActionForm } from './ActionForm'
import { useConnector, useConnectors } from './api'
import { selectClassName } from './selectStyles'

/** Pick a connector and one of its actions, then fill and test the generated form. */
export function ConnectorsPage() {
  const connectors = useConnectors()
  const [connectorKey, setConnectorKey] = useState<string>()
  const [actionKey, setActionKey] = useState<string>()
  const connector = useConnector(connectorKey)
  const action = connector.data?.actions.find((a) => a.key === actionKey) ?? connector.data?.actions[0]

  return (
    <div className="grid gap-6 md:grid-cols-[240px_1fr]">
      <nav aria-label="Connectors" className="space-y-2">
        {connectors.isPending && <p className="text-sm text-muted-foreground">Loading connectors…</p>}
        {connectors.isError && <p className="text-sm text-destructive">{connectors.error.message}</p>}
        {connectors.data?.map((c) => (
          <button
            key={c.key}
            type="button"
            onClick={() => {
              setConnectorKey(c.key)
              setActionKey(undefined)
            }}
            className={cn(
              'w-full rounded-lg border p-3 text-left hover:bg-muted',
              c.key === connectorKey && 'border-primary bg-muted',
            )}
          >
            <div className="font-medium">{c.name}</div>
            <div className="text-xs text-muted-foreground">{c.description}</div>
          </button>
        ))}
      </nav>

      <section>
        {connectorKey === undefined && <p className="text-muted-foreground">Pick a connector to configure a step.</p>}
        {connector.isLoading && <p className="text-muted-foreground">Loading…</p>}
        {connector.isError && <p className="text-destructive">{connector.error.message}</p>}
        {connector.data && action && (
          <Card>
            <CardHeader>
              <CardTitle>{connector.data.name}</CardTitle>
              <CardDescription>{action.description}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {connector.data.actions.length > 1 && (
                <select
                  aria-label="Action"
                  className={selectClassName}
                  value={action.key}
                  onChange={(event) => setActionKey(event.target.value)}
                >
                  {connector.data.actions.map((a) => (
                    <option key={a.key} value={a.key}>
                      {a.name}
                    </option>
                  ))}
                </select>
              )}
              <ActionForm key={`${connector.data.key}.${action.key}`} connectorKey={connector.data.key} action={action} />
            </CardContent>
          </Card>
        )}
      </section>
    </div>
  )
}
