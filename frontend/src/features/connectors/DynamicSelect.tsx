import { useState } from 'react'
import { useFormContext } from 'react-hook-form'
import type { FieldDefinition } from '../../api/client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useFieldOptions } from './api'
import type { FormValues } from './formSchema'
import { selectClassName } from './selectStyles'

type Props = {
  id: string
  connectorKey: string
  actionKey: string
  field: FieldDefinition
}

export function DynamicSelect({ id, connectorKey, actionKey, field }: Props) {
  const { register, getValues } = useFormContext<FormValues>()
  const [manual, setManual] = useState(false)
  const dependsOnValues = Object.fromEntries(field.dependsOn.map((key) => [key, getValues(key)]))
  const options = useFieldOptions(connectorKey, actionKey, field.key, dependsOnValues)

  if (manual) {
    return (
      <div className="space-y-1">
        <Input id={id} placeholder="Type a value" {...register(field.key)} />
        <button type="button" className="text-xs underline" onClick={() => setManual(false)}>
          Choose from the list
        </button>
      </div>
    )
  }

  return (
    <div className="space-y-1">
      <div className="flex gap-2">
        <select id={id} className={selectClassName} disabled={options.isPending} {...register(field.key)}>
          <option value="">{options.isPending ? 'Loading…' : 'Select…'}</option>
          {options.data?.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <Button type="button" variant="outline" onClick={() => options.refetch()} disabled={options.isFetching}>
          Refresh
        </Button>
      </div>
      {options.isError && <p className="text-sm text-destructive">{options.error.message}</p>}
      {options.isSuccess && options.data.length === 0 && (
        <p className="text-sm text-muted-foreground">No options found.</p>
      )}
      {field.allowCustomValue && (
        <button type="button" className="text-xs underline" onClick={() => setManual(true)}>
          Enter manually
        </button>
      )}
    </div>
  )
}
