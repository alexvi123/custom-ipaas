import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import type { ActionDefinition } from '../../api/client'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { FieldErrors, useTestAction } from './api'
import { FieldInput } from './FieldInput'
import { buildFormSchema, defaultValues, toRequestValues } from './formSchema'
import { TestResultPanel } from './TestResultPanel'

type Props = {
  connectorKey: string
  action: ActionDefinition
}

/** A form for any action, built only from its field definitions, plus the "Test step" button. */
export function ActionForm({ connectorKey, action }: Props) {
  const schema = useMemo(() => buildFormSchema(action.fields), [action.fields])
  const form = useForm({ resolver: zodResolver(schema), defaultValues: defaultValues(action.fields) })
  const testAction = useTestAction(connectorKey, action.key)

  const onSubmit = form.handleSubmit((values) =>
    testAction.mutate(toRequestValues(action.fields, values), {
      onError: (error) => {
        if (error instanceof FieldErrors) {
          Object.entries(error.errors).forEach(([key, message]) => form.setError(key, { message }))
        }
      },
    }),
  )

  return (
    <FormProvider {...form}>
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {action.fields.map((field) => (
          <FieldInput key={field.key} connectorKey={connectorKey} actionKey={action.key} field={field} />
        ))}
        <Button type="submit" disabled={testAction.isPending}>
          {testAction.isPending ? 'Running…' : 'Test step'}
        </Button>
        {testAction.data && <TestResultPanel result={testAction.data} />}
        {testAction.error && !(testAction.error instanceof FieldErrors) && (
          <Alert variant="destructive">
            <AlertDescription>{testAction.error.message}</AlertDescription>
          </Alert>
        )}
      </form>
    </FormProvider>
  )
}
