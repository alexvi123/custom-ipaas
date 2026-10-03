import { Link } from 'react-router'
import { useRuns } from './api'
import { formatDuration, formatTime } from './format'
import { StatusBadge } from './StatusBadge'

export function RunsList({ workflowId }: { workflowId: string }) {
  const runs = useRuns(workflowId)

  if (runs.isPending) return <p className="text-sm text-muted-foreground">Loading runs…</p>
  if (runs.isError) return <p className="text-sm text-destructive">{runs.error.message}</p>
  if (runs.data.length === 0) {
    return <p className="text-sm text-muted-foreground">No runs yet. Call the webhook to start one.</p>
  }

  return (
    <ul className="divide-y rounded-lg border">
      {runs.data.map((run) => (
        <li key={run.id}>
          <Link to={`/runs/${run.id}`} className="flex items-center gap-3 p-3 text-sm hover:bg-muted">
            <StatusBadge status={run.status} />
            <span>{formatTime(run.createdAt)}</span>
            <span className="text-muted-foreground">{formatDuration(run.durationMs)}</span>
            {run.error && <span className="min-w-0 flex-1 truncate text-destructive">{run.error}</span>}
          </Link>
        </li>
      ))}
    </ul>
  )
}
