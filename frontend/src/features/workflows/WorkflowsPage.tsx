import { Link } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { buttonVariants } from '@/components/ui/button'
import { useWorkflows } from './api'

export function WorkflowsPage() {
  const workflows = useWorkflows()

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-medium">Workflows</h2>
        <Link to="/workflows/new" className={buttonVariants()}>
          New workflow
        </Link>
      </div>
      {workflows.isPending && <p className="text-muted-foreground">Loading…</p>}
      {workflows.isError && <p className="text-destructive">{workflows.error.message}</p>}
      {workflows.data?.length === 0 && (
        <p className="text-muted-foreground">No workflows yet. Create one from the starter template.</p>
      )}
      <ul className="divide-y rounded-lg border">
        {workflows.data?.map((workflow) => (
          <li key={workflow.id}>
            <Link to={`/workflows/${workflow.id}`} className="flex items-center justify-between p-3 hover:bg-muted">
              <span className="font-medium">{workflow.name}</span>
              <span className="flex items-center gap-2 text-xs text-muted-foreground">
                v{workflow.version}
                <Badge variant={workflow.active ? 'default' : 'outline'}>{workflow.active ? 'Active' : 'Inactive'}</Badge>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
