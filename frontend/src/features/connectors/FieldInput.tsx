import type { ReactNode } from 'react'
import { useFormContext } from 'react-hook-form'
import type { FieldDefinition } from '../../api/client'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import { DynamicSelect } from './DynamicSelect'
import type { FormValues } from './formSchema'
import { selectClassName } from './selectStyles'

type Props = {
  connectorKey: string
  actionKey: string
  field: FieldDefinition
}

export function FieldInput({ connectorKey, actionKey, field }: Props) {
  const {
    register,
    formState: { errors },
  } = useFormContext<FormValues>()
  const id = `field-${field.key}`
  const error = errors[field.key]?.message

  let control: ReactNode
  switch (field.type) {
    case 'TEXT':
    case 'JSON':
      control = (
        <Textarea
          id={id}
          placeholder={field.placeholder}
          className={cn(field.type === 'JSON' && 'font-mono')}
          {...register(field.key)}
        />
      )
      break
    case 'BOOLEAN':
      control = <input id={id} type="checkbox" className="size-4" {...register(field.key)} />
      break
    case 'SELECT':
      control = field.dynamicOptions ? (
        <DynamicSelect id={id} connectorKey={connectorKey} actionKey={actionKey} field={field} />
      ) : (
        <select id={id} className={selectClassName} {...register(field.key)}>
          {!field.required && <option value="">—</option>}
          {field.options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      )
      break
    case 'NUMBER':
      control = <Input id={id} inputMode="decimal" placeholder={field.placeholder} {...register(field.key)} />
      break
    default:
      control = <Input id={id} placeholder={field.placeholder} {...register(field.key)} />
  }

  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>
        {field.label}
        {field.required && <span className="text-destructive">*</span>}
      </Label>
      {control}
      {field.help && <p className="text-xs text-muted-foreground">{field.help}</p>}
      {typeof error === 'string' && <p className="text-sm text-destructive">{error}</p>}
    </div>
  )
}
