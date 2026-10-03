import type { TestActionResponse } from '../../api/client'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

export function TestResultPanel({ result }: { result: TestActionResponse }) {
  const hasOutput = Object.keys(result.output).length > 0
  return (
    <Alert variant={result.success ? 'default' : 'destructive'}>
      <AlertTitle>
        {result.success ? 'Success' : 'Failed'} · {result.durationMs} ms
      </AlertTitle>
      <AlertDescription>
        {result.error && <p>{result.error}</p>}
        {hasOutput && (
          <pre className="mt-2 max-h-64 overflow-auto rounded bg-muted p-2 text-xs text-foreground">
            {JSON.stringify(result.output, null, 2)}
          </pre>
        )}
      </AlertDescription>
    </Alert>
  )
}
