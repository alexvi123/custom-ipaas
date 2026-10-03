import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

type Props = {
  webhookPath: string
  active: boolean
}

export function WebhookPanel({ webhookPath, active }: Props) {
  const [copied, setCopied] = useState(false)
  const url = `${window.location.origin}${webhookPath}`
  const powershell = `Invoke-RestMethod -Method Post -Uri ${url} -ContentType 'application/json' -Body '{"name": "Alex"}'`
  const curl = `curl -X POST ${url} -H "Content-Type: application/json" -d '{"name": "Alex"}'`

  async function copyUrl() {
    await navigator.clipboard.writeText(url)
    setCopied(true)
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Webhook</CardTitle>
        <CardDescription>
          {active
            ? 'POST JSON to this URL to start a run.'
            : 'Activate the workflow to accept calls (inactive workflows answer 409).'}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex items-center gap-2">
          <code className="min-w-0 flex-1 truncate rounded bg-muted px-2 py-1 text-xs">{url}</code>
          <Button type="button" variant="outline" onClick={copyUrl}>
            {copied ? 'Copied' : 'Copy'}
          </Button>
        </div>
        <div className="space-y-1">
          <p className="text-xs text-muted-foreground">PowerShell</p>
          <pre className="overflow-x-auto rounded bg-muted p-2 text-xs">{powershell}</pre>
        </div>
        <div className="space-y-1">
          <p className="text-xs text-muted-foreground">curl</p>
          <pre className="overflow-x-auto rounded bg-muted p-2 text-xs">{curl}</pre>
        </div>
      </CardContent>
    </Card>
  )
}
