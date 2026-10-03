import { Link, useParams } from 'react-router'
import type { StepRunView } from '../../api/client'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useRun } from './api'
import { formatDuration, formatTime } from './format'
import { StatusBadge } from './StatusBadge'

function Json({ label, value }: { label: string; value: unknown }) {
  return (
    <details className="text-xs">
      <summary className="cursor-pointer text-muted-foreground">{label}</summary>
      <pre className="mt-1 max-h-64 overflow-auto rounded bg-muted p-2">{JSON.stringify(value, null, 2)}</pre>
    </details>
  )
}

function StepCard({ step }: { step: StepRunView }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <span className="text-muted-foreground">{step.position + 1}.</span>
          {step.key}
          <span className="font-normal text-muted-foreground">
            {step.connector}.{step.action}
          </span>
          <span className="ml-auto flex items-center gap-2 font-normal">
            <span className="text-xs text-muted-foreground">{formatDuration(step.durationMs)}</span>
            <StatusBadge status={step.status} />
          </span>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {step.error && <p className="text-sm text-destructive">{step.error}</p>}
        {step.input && <Json label="Input" value={step.input} />}
        {step.output && <Json label="Output" value={step.output} />}
      </CardContent>
    </Card>
  )
}

export function RunDetailPage() {
  const { id } = useParams()
  const run = useRun(id)

  if (run.isPending) return <p className="text-muted-foreground">Loading…</p>
  if (run.isError) return <p className="text-destructive">{run.error.message}</p>

  const data = run.data
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="text-lg font-medium">Run</h2>
        <StatusBadge status={data.status} />
        <span className="text-sm text-muted-foreground">
          {formatTime(data.createdAt)} · {formatDuration(data.durationMs)} · workflow version v{data.workflowVersion}
        </span>
        <Link to={`/workflows/${data.workflowId}`} className="ml-auto text-sm underline">
          Back to workflow
        </Link>
      </div>
      {data.error && (
        <Alert variant="destructive">
          <AlertDescription>{data.error}</AlertDescription>
        </Alert>
      )}
      <Json label="Trigger (webhook data)" value={data.trigger} />
      <div className="space-y-3">
        {data.steps.map((step) => (
          <StepCard key={step.key} step={step} />
        ))}
      </div>
    </div>
  )
}
