import { type FormEvent, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import type { WorkflowDetails } from '../../api/client'
import { FieldErrors } from '../../api/problems'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { RunsList } from '../runs/RunsList'
import { useSaveWorkflow, useSetActive, useWorkflow } from './api'
import { starterText } from './starterTemplate'
import { WebhookPanel } from './WebhookPanel'

function isJsonObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function WorkflowEditorPage() {
  const { id } = useParams()
  const workflow = useWorkflow(id)

  if (id !== undefined && workflow.isPending) return <p className="text-muted-foreground">Loading…</p>
  if (workflow.isError) return <p className="text-destructive">{workflow.error.message}</p>
  return <WorkflowEditor key={id ?? 'new'} workflow={workflow.data} />
}

function WorkflowEditor({ workflow }: { workflow: WorkflowDetails | undefined }) {
  const navigate = useNavigate()
  const [name, setName] = useState(workflow?.name ?? 'My first workflow')
  const [text, setText] = useState(workflow ? JSON.stringify(workflow.definition, null, 2) : starterText)
  const [jsonError, setJsonError] = useState<string>()
  const save = useSaveWorkflow(workflow?.id)

  function onSave(event: FormEvent) {
    event.preventDefault()
    let parsed: unknown
    try {
      parsed = JSON.parse(text)
    } catch (error) {
      setJsonError(`Not valid JSON: ${error instanceof Error ? error.message : String(error)}`)
      return
    }
    if (!isJsonObject(parsed)) {
      setJsonError('The definition must be a JSON object')
      return
    }
    setJsonError(undefined)
    save.mutate(
      { name, definition: parsed },
      { onSuccess: (saved) => workflow === undefined && navigate(`/workflows/${saved.id}`) },
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-lg font-medium">{workflow ? workflow.name : 'New workflow'}</h2>
        {workflow && <ActiveToggle workflow={workflow} />}
      </div>

      <form onSubmit={onSave} className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="workflow-name">Name</Label>
          <Input id="workflow-name" value={name} onChange={(event) => setName(event.target.value)} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="workflow-definition">Definition (JSON)</Label>
          <Textarea
            id="workflow-definition"
            className="min-h-96 font-mono text-xs"
            spellCheck={false}
            value={text}
            onChange={(event) => setText(event.target.value)}
          />
          <p className="text-xs text-muted-foreground">
            Steps run in order. Use {'{{trigger.body.<field>}}'} for the webhook data and{' '}
            {'{{steps.<key>.output.<field>}}'} for an earlier step's result.
          </p>
        </div>

        {jsonError && <p className="text-sm text-destructive">{jsonError}</p>}
        {save.error instanceof FieldErrors && (
          <Alert variant="destructive">
            <AlertTitle>The definition has problems</AlertTitle>
            <AlertDescription>
              <ul className="list-disc pl-4">
                {Object.entries(save.error.errors).map(([path, message]) => (
                  <li key={path}>
                    <code>{path}</code>: {message}
                  </li>
                ))}
              </ul>
            </AlertDescription>
          </Alert>
        )}
        {save.error && !(save.error instanceof FieldErrors) && (
          <p className="text-sm text-destructive">{save.error.message}</p>
        )}

        <div className="flex items-center gap-3">
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : workflow ? 'Save new version' : 'Create workflow'}
          </Button>
          {workflow && <span className="text-xs text-muted-foreground">Current version: v{workflow.version}</span>}
        </div>
      </form>

      {workflow && (
        <>
          <WebhookPanel webhookPath={workflow.webhookPath} active={workflow.active} />
          <section className="space-y-2">
            <h3 className="font-medium">Runs</h3>
            <RunsList workflowId={workflow.id} />
          </section>
        </>
      )}
    </div>
  )
}

function ActiveToggle({ workflow }: { workflow: WorkflowDetails }) {
  const setActive = useSetActive(workflow.id)
  return (
    <div className="flex items-center gap-2">
      <Badge variant={workflow.active ? 'default' : 'outline'}>{workflow.active ? 'Active' : 'Inactive'}</Badge>
      <Button
        type="button"
        variant="outline"
        disabled={setActive.isPending}
        onClick={() => setActive.mutate(!workflow.active)}
      >
        {workflow.active ? 'Deactivate' : 'Activate'}
      </Button>
      {setActive.isError && <span className="text-sm text-destructive">{setActive.error.message}</span>}
    </div>
  )
}
