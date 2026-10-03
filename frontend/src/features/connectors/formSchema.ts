import { z } from 'zod'
import type { FieldDefinition } from '../../api/client'

export type FormValues = Record<string, unknown>

/** Builds the form's validation rules from the connector's field definitions. */
export function buildFormSchema(fields: FieldDefinition[]) {
  const shape: Record<string, z.ZodType> = {}
  for (const field of fields) {
    shape[field.key] =
      field.type === 'BOOLEAN'
        ? z.boolean()
        : z.string().superRefine((value, ctx) => {
            const message = validateText(field, value)
            if (message) ctx.addIssue({ code: 'custom', message })
          })
  }
  return z.object(shape)
}

function validateText(field: FieldDefinition, raw: string): string | undefined {
  const value = raw.trim()
  if (value === '') return field.required ? 'Required' : undefined
  switch (field.type) {
    case 'NUMBER':
      return Number.isFinite(Number(value)) ? undefined : 'Must be a number'
    case 'JSON':
      try {
        JSON.parse(value)
        return undefined
      } catch {
        return 'Must be valid JSON'
      }
    case 'SELECT': {
      const restricted = field.options.length > 0 && !field.allowCustomValue && !field.dynamicOptions
      const allowed = field.options.map((option) => option.value)
      return restricted && !allowed.includes(value) ? `Must be one of: ${allowed.join(', ')}` : undefined
    }
    default:
      return undefined
  }
}

export function defaultValues(fields: FieldDefinition[]): FormValues {
  return Object.fromEntries(
    fields.map((field) => [field.key, field.type === 'BOOLEAN' ? field.defaultValue === 'true' : (field.defaultValue ?? '')]),
  )
}

export function toRequestValues(fields: FieldDefinition[], values: FormValues): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  for (const field of fields) {
    const value = values[field.key]
    if (typeof value === 'boolean') {
      result[field.key] = value
      continue
    }
    const text = typeof value === 'string' ? value.trim() : ''
    if (text === '') continue
    result[field.key] = field.type === 'NUMBER' ? Number(text) : text
  }
  return result
}
