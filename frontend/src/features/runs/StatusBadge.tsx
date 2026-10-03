import type { RunStatus, StepStatus } from '../../api/client'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

const styles: Record<RunStatus | StepStatus, string> = {
  PENDING: 'border-border bg-transparent text-muted-foreground',
  RUNNING: 'bg-blue-600 text-white',
  SUCCEEDED: 'bg-emerald-600 text-white',
  FAILED: 'bg-destructive text-white',
  SKIPPED: 'border-border bg-transparent text-muted-foreground line-through',
}

export function StatusBadge({ status }: { status: RunStatus | StepStatus }) {
  return <Badge className={cn('font-mono text-[10px]', styles[status])}>{status}</Badge>
}
